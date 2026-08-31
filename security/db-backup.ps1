# ============================================================================
# db-backup.ps1  --  nightly encrypted DB backup to Cloudflare R2
# ----------------------------------------------------------------------------
# Flow: pg_dump (inside container) -> docker cp out -> age encrypt -> rclone -> R2
# On failure: Discord alert (reuses security\discord-webhook.txt). Success is silent.
# Run manually to test, then register in Task Scheduler (daily, low-traffic hour).
# Restore needs the age PRIVATE key. It is held in a password manager, out of band --
# never in this repo or on the server. Where it lives is deliberately not written down here.
# ============================================================================
$ErrorActionPreference = 'Stop'

$stamp       = Get-Date -Format 'yyyyMMdd_HHmmss'
$container   = 'ott-postgres'
$db          = 'ott_project_db'
$dbUser      = 'root'
$agePub      = 'age1nzkslmrgdcec2a3v45ttydmzptfmnrt6774qp3we933csmd9a5rqlsa2zc'
$remote      = 'r2:ott-db-backups'
$webhookFile = 'C:\solo-project\ott-project\security\discord-webhook.txt'

$tmpSql        = Join-Path $env:TEMP ("db_{0}.sql" -f $stamp)
$tmpEnc        = "$tmpSql.age"
$tmpGlobals    = Join-Path $env:TEMP ("globals_{0}.sql" -f $stamp)
$tmpGlobalsEnc = "$tmpGlobals.age"

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
  # 1) dump straight onto the host. cmd /c owns the redirect so the bytes never pass through
  #    PowerShell's output encoding - the same trick log-backup.ps1 uses for `docker logs`.
  #
  #    [2026-08-30] This used to write inside the container (pg_dump -f /tmp/... ) and pull the
  #    file out with docker cp. That broke when ott-postgres went read_only: /tmp is no longer
  #    writable, and adding a /tmp tmpfs does NOT fix it either - `docker cp` cannot read from a
  #    tmpfs mount on this host (measured: cp from a tmpfs path fails with "Could not find the
  #    file", the same path on a volume succeeds). Streaming needs no writable path in the
  #    container at all, which is why it is the better fix rather than a workaround.
  #
  #    Verified equivalent, not assumed: a streamed dump and an in-container dump of the same
  #    database came out the same length and differed only in the two \restrict/\unrestrict
  #    lines, which carry a nonce pg_dump regenerates every run (126 bytes, 2 lines). No
  #    encoding damage - the original reason for dumping inside the container does not apply.
  cmd /c "docker exec $container pg_dump -U $dbUser $db > `"$tmpSql`""
  if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)" }
  if (-not (Test-Path $tmpSql) -or (Get-Item $tmpSql).Length -eq 0) { throw "pg_dump produced empty file" }

  # 2) dump the cluster globals (roles) as a SECOND file.
  #
  #    [2026-08-30] Added after restore-drill.ps1 proved this backup could not be restored:
  #    restoring db_*.sql onto a fresh cluster died at line 189145 with
  #        ERROR:  role "ott_app" does not exist
  #    pg_dump covers ONE database; roles live at cluster level and only pg_dumpall emits them.
  #    The dump has 87 GRANT lines naming ott_app (the least-privilege runtime account from
  #    security/initdb), so without the roles every one of them fails. Before this, the backup
  #    reported success every night while being unrestorable.
  #
  #    Kept as a separate object rather than concatenated: globals must be applied to the
  #    cluster BEFORE the database dump, and keeping them apart makes that order explicit in
  #    the runbook and lets the drill apply each with ON_ERROR_STOP separately.
  #    It carries role password hashes, which is exactly why it is age-encrypted like the rest.
  cmd /c "docker exec $container pg_dumpall -U $dbUser --globals-only > `"$tmpGlobals`""
  if ($LASTEXITCODE -ne 0) { throw "pg_dumpall --globals-only failed (exit $LASTEXITCODE)" }
  if (-not (Test-Path $tmpGlobals) -or (Get-Item $tmpGlobals).Length -eq 0) { throw "globals dump is empty" }
  if (-not (Select-String -Path $tmpGlobals -Pattern '^CREATE ROLE ott_app;' -Quiet)) {
    throw "globals dump does not contain the ott_app role - the app account would lose all grants on restore"
  }

  # 3) encrypt both with the age public key (decrypt requires the private key, held out of band)
  age -r $agePub -o $tmpEnc $tmpSql
  age -r $agePub -o $tmpGlobalsEnc $tmpGlobals

  # 4) upload the encrypted files to R2
  rclone copy $tmpEnc $remote
  rclone copy $tmpGlobalsEnc $remote

  # 5) clean up local temp files. The plaintext globals hold password hashes - do not leave them.
  Remove-Item $tmpSql, $tmpEnc, $tmpGlobals, $tmpGlobalsEnc -Force -ErrorAction SilentlyContinue
  Write-Host "backup OK: db_$stamp.sql.age + globals_$stamp.sql.age -> $remote"
}
catch {
  Remove-Item $tmpSql, $tmpEnc, $tmpGlobals, $tmpGlobalsEnc -Force -ErrorAction SilentlyContinue
  Send-Alert (":rotating_light: DB backup FAILED $stamp -- $($_.Exception.Message)")
  Write-Host "backup FAILED: $($_.Exception.Message)"
  exit 1
}
