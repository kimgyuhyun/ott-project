# ============================================================================
# db-backup.ps1  --  nightly encrypted DB backup to Cloudflare R2
# ----------------------------------------------------------------------------
# Flow: pg_dump (inside container) -> docker cp out -> age encrypt -> rclone -> R2
# On failure: Discord alert (reuses security\discord-webhook.txt). Success is silent.
# Run manually to test, then register in Task Scheduler (daily, low-traffic hour).
# Restore needs the age PRIVATE key (backed up in Bitwarden: "ott key").
# ============================================================================
$ErrorActionPreference = 'Stop'

$stamp       = Get-Date -Format 'yyyyMMdd_HHmmss'
$container   = 'ott-postgres'
$db          = 'ott_project_db'
$dbUser      = 'root'
$agePub      = 'age1nzkslmrgdcec2a3v45ttydmzptfmnrt6774qp3we933csmd9a5rqlsa2zc'
$remote      = 'r2:ott-db-backups'
$webhookFile = 'C:\solo-project\ott-project\security\discord-webhook.txt'

$tmpSql = Join-Path $env:TEMP ("db_{0}.sql" -f $stamp)
$tmpEnc = "$tmpSql.age"
$inCon  = "/tmp/db_$stamp.sql"

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
  # 1) dump inside the container (clean UTF-8, no PowerShell encoding mangling), copy out
  docker exec $container pg_dump -U $dbUser $db -f $inCon
  docker cp "${container}:$inCon" $tmpSql
  docker exec $container rm -f $inCon
  if (-not (Test-Path $tmpSql) -or (Get-Item $tmpSql).Length -eq 0) { throw "pg_dump produced empty file" }

  # 2) encrypt with age public key (decrypt requires the private key from Bitwarden)
  age -r $agePub -o $tmpEnc $tmpSql

  # 3) upload the encrypted file to R2
  rclone copy $tmpEnc $remote

  # 4) clean up local temp files
  Remove-Item $tmpSql, $tmpEnc -Force -ErrorAction SilentlyContinue
  Write-Host "backup OK: db_$stamp.sql.age -> $remote"
}
catch {
  Remove-Item $tmpSql, $tmpEnc -Force -ErrorAction SilentlyContinue
  Send-Alert (":rotating_light: DB backup FAILED $stamp -- $($_.Exception.Message)")
  Write-Host "backup FAILED: $($_.Exception.Message)"
  exit 1
}
