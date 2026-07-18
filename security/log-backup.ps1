# ============================================================================
# log-backup.ps1  --  nightly encrypted off-host LOG archive to Cloudflare R2
# ----------------------------------------------------------------------------
# Flow: docker logs (ott-nginx + ott-app) -> zip -> age encrypt -> rclone -> R2
# Source rationale: logback routes every logger (root/payment/AUTH_AUDIT) to the
#   CONSOLE appender, and nginx access/error are symlinked to stdout/stderr, so
#   `docker logs <container>` is the complete stream for each. No file copy or
#   volume mount needed.
# Why off-host: after the 2026-06 compromise, forensic logs must survive a host
#   or container takeover. This keeps a tamper-isolated copy in R2.
# Caveat: a deploy recreates containers and resets `docker logs`, so this daily
#   snapshot captures the current container's logs only (accepted daily-archive
#   tradeoff, not real-time shipping).
# On failure: Discord alert (reuses security\discord-webhook.txt). Success is silent.
# Reading a backup needs the age PRIVATE key (backed up in Bitwarden: "ott key").
# Run manually to test, then register in Task Scheduler (daily, low-traffic hour).
# ============================================================================
$ErrorActionPreference = 'Stop'

$stamp       = Get-Date -Format 'yyyyMMdd_HHmmss'
$containers  = @('ott-nginx', 'ott-app')
$agePub      = 'age1nzkslmrgdcec2a3v45ttydmzptfmnrt6774qp3we933csmd9a5rqlsa2zc'
$remote      = 'r2:ott-db-backups/logs'
$webhookFile = 'C:\solo-project\ott-project\security\discord-webhook.txt'

$work = Join-Path $env:TEMP ("ottlogs_{0}" -f $stamp)
$zip  = Join-Path $env:TEMP ("ottlogs_{0}.zip" -f $stamp)
$enc  = "$zip.age"

function Send-Alert($msg) {
  try {
    if (-not (Test-Path $webhookFile)) { return }
    $url = ([regex]::Match((Get-Content $webhookFile -Raw), 'https://\S+')).Value
    if (-not $url) { return }
    $body  = @{ content = $msg } | ConvertTo-Json
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 10 | Out-Null
  } catch { }
}

try {
  New-Item -ItemType Directory -Path $work -Force | Out-Null

  # 1) capture each container's full log stream (stdout+stderr merged via cmd to
  #    avoid PS 5.1 wrapping native stderr as terminating errors)
  foreach ($c in $containers) {
    $out = Join-Path $work ("{0}_{1}.log" -f $c, $stamp)
    cmd /c "docker logs `"$c`" > `"$out`" 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "docker logs failed for $c (exit $LASTEXITCODE)" }
  }

  # 2) zip captured logs into a single archive (age encrypts one file)
  Compress-Archive -Path (Join-Path $work '*.log') -DestinationPath $zip -Force
  if (-not (Test-Path $zip) -or (Get-Item $zip).Length -eq 0) { throw "log archive is empty" }

  # 3) encrypt with age public key (decrypt requires the private key from Bitwarden)
  age -r $agePub -o $enc $zip

  # 4) upload the encrypted archive to R2 (logs/ prefix keeps it apart from db dumps)
  rclone copy $enc $remote

  # 5) clean up local temp
  Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item $zip, $enc -Force -ErrorAction SilentlyContinue
  Write-Host "log backup OK: ottlogs_$stamp.zip.age -> $remote"
}
catch {
  Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item $zip, $enc -Force -ErrorAction SilentlyContinue
  Send-Alert (":rotating_light: LOG backup FAILED $stamp -- $($_.Exception.Message)")
  Write-Host "log backup FAILED: $($_.Exception.Message)"
  exit 1
}
