# check-observability.ps1 - PLATFORM 9절 (관측) invariants, checked against the running stack.
#
# Called by deploy.ps1 and deploy-rolling.ps1 after `up`, next to the other two post-deploy
# checks. Separate file for the same reason they are separate from each other: this one is
# PLATFORM 9, and a file that says what rule it enforces can be trusted to still enforce it.
#
# Why it exists (2026-09-02):
#   loki-config.yml, config.alloy and the grafana provisioning tree are bind mounts, and
#   compose recreates a container when the service DEFINITION changes, never when the
#   CONTENT of a mounted file does. nginx and prometheus get around that with a validated
#   reload in the deploy scripts; squid and sockd get around it with outcome probes;
#   rabbitmq now has a value assert. These four had nothing at all - an edit to any of them
#   reached disk and the running process kept its old config, silently.
#
#   The failure that costs the most is not a rejected config, it is a working one that
#   nobody is using: if log shipping breaks, nothing breaks visibly. The site serves fine,
#   every other check passes, and the gap is discovered when someone goes looking for logs
#   that were never collected - which is exactly when they are needed (PLATFORM 11절:
#   "침해된 호스트의 로그는 증거로 쓸 수 없다" only holds if the logs exist at all).
#
# Why not a mtime check (config newer than container start)? It was the obvious general
#   answer and it does not work here. cd.yml re-writes every single config file on every
#   deploy to normalise line endings (WriteAllText, unconditional), so mtime moves even
#   when the bytes do not - measured 2026-09-02: all three configs stamped 05:17:45 by that
#   day's deploy while their containers dated from 08-29. That check would have failed
#   every deploy and been switched off within a week.
#
# So: one drift assert where the process exposes what it actually loaded (loki), and
# outcome probes everywhere else - the squid/sockd pattern.
#
# Probe origin is the prometheus container: it sits on the monitoring network and has a
# usable wget. If it is down every probe here fails, which is the right answer - prometheus
# being down IS an observability failure, not a broken test.
#
# Usage:
#   & .\security\check-observability.ps1 -ComposeFiles $ComposeFiles
param(
    [Parameter(Mandatory = $true)][string[]]$ComposeFiles
)

$ErrorActionPreference = 'Stop'

Write-Host '=== VERIFY observability pipeline (PLATFORM section 9) ==='

# Loki normalises durations on the way out - the file says 168h, /config answers 1w - so a
# string compare would fail on a correct config. Both sides go through this first.
# Anything that is not a whole number of s/m/h/d/w units fails loudly rather than
# comparing as zero.
function ConvertTo-Seconds {
    param([string]$Value)
    $v = $Value.Trim()
    if ($v -notmatch '^(\d+[smhdw])+$') { throw "SECURITY INVARIANT FAILED: cannot interpret duration '$Value'" }
    $mult = @{ s = 1; m = 60; h = 3600; d = 86400; w = 604800 }
    $total = 0
    foreach ($m in [regex]::Matches($v, '(\d+)([smhdw])')) {
        $total += [int64]$m.Groups[1].Value * $mult[$m.Groups[2].Value]
    }
    return $total
}

# Both the mounted file and /config are flat YAML with top-level sections at column 0, so
# tracking the current section is enough to tell limits_config.retention_period (the one we
# set) from table_manager.retention_period (a default that is always 0s). Inline comments
# are stripped: the mounted file writes "168h   # 7일 보관".
function Get-YamlValueInSection {
    param([string[]]$Lines, [string]$Section, [string]$Key)
    $cur = ''
    foreach ($line in $Lines) {
        if ($line -match '^([a-z_]+):') { $cur = $Matches[1]; continue }
        if ($cur -eq $Section -and $line -match "^\s+$([regex]::Escape($Key)):\s*(.+)$") {
            return ($Matches[1] -split '#')[0].Trim()
        }
    }
    return $null
}

function Invoke-Probe {
    param([string]$ProbeId, [string]$Url, [int]$TimeoutSec = 8)
    $out = docker exec $ProbeId wget -q -O - --timeout=$TimeoutSec $Url
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($out | Out-String)
}

$ids = docker compose @ComposeFiles ps -q
if (-not $ids) { throw 'observability check found no running containers - it must not pass by having nothing to look at' }

# Select by compose service label, not container name, for the same reason as the DB check.
$svcIds = @{}
foreach ($id in $ids) {
    $insp = (docker inspect $id | Out-String | ConvertFrom-Json)[0]
    $svc  = $insp.Config.Labels.'com.docker.compose.service'
    if ($svc -in @('loki', 'alloy', 'grafana', 'prometheus')) { $svcIds[$svc] = $id }
}
foreach ($need in @('loki', 'alloy', 'grafana', 'prometheus')) {
    if (-not $svcIds.ContainsKey($need)) { throw "SECURITY INVARIANT FAILED: no '$need' container in the deployed file set - the observability invariants could not be checked. docker-compose.monitoring.yml is part of every deploy path, so this means the stack changed and this check needs updating, not skipping." }
}
$probe = $svcIds['prometheus']

# --- 1) Loki: is it running the config that is mounted right now? -----------------
# The one place in this stack where the process reports its effective config back, so the
# same assert shape as the rabbitmq watermark is available: read what is mounted, ask what
# was loaded, require agreement. retention_period is the value worth guarding - it decides
# how far back evidence exists, and getting it wrong is invisible until someone needs it.
$mounted = docker exec $svcIds['loki'] cat /etc/loki/loki-config.yml
if ($LASTEXITCODE -ne 0) { throw 'SECURITY INVARIANT FAILED: cannot read the mounted /etc/loki/loki-config.yml' }
$mountedRetention = Get-YamlValueInSection -Lines $mounted -Section 'limits_config' -Key 'retention_period'
if (-not $mountedRetention) { throw 'SECURITY INVARIANT FAILED: no limits_config.retention_period in the mounted loki config - this check cannot confirm the retention window' }

$effectiveRaw = Invoke-Probe -ProbeId $probe -Url 'http://ott-loki:3100/config'
if (-not $effectiveRaw) { throw 'SECURITY INVARIANT FAILED: loki did not answer /config - cannot confirm which configuration it is running' }
$effectiveRetention = Get-YamlValueInSection -Lines ($effectiveRaw -split "`r?`n") -Section 'limits_config' -Key 'retention_period'
if (-not $effectiveRetention) { throw 'SECURITY INVARIANT FAILED: loki /config reported no limits_config.retention_period' }

$mSec = ConvertTo-Seconds $mountedRetention
$eSec = ConvertTo-Seconds $effectiveRetention
if ($mSec -ne $eSec) {
    throw "SECURITY INVARIANT FAILED: loki is running a log retention of $effectiveRetention but the mounted config now says $mountedRetention. A bind-mounted file changed without the container being recreated, so the retention you think you have is not the one in effect. Recreate it: docker compose <files> up -d --force-recreate loki"
}
Write-Host "  loki       : retention $mountedRetention effective (matches mounted config)"

# --- 2) Alloy: is it actually shipping? -------------------------------------------
# Alloy binds its UI to 127.0.0.1 and its image has no wget, so it cannot be probed
# directly - and "the container is running" was never the question anyway. The outcome is
# what matters: are the backend's log lines reaching loki. After a deploy the app has just
# restarted and logged its startup, so this is present unless the pipeline is broken.
#
# 15 minutes, and only ott-backend: measured 2026-09-02, a 5-minute window holds only the
# services that log continuously (frontend, nginx, ott-backend) while postgres, kafka and
# rabbitmq are quiet for longer. Asserting on the quiet ones would fail on a healthy stack.
# The retry is for the ingestion lag right after a deploy, not for flakiness cover.
$found = $false
foreach ($attempt in 1..3) {
    $now   = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $start = $now - 900
    $body  = Invoke-Probe -ProbeId $probe -Url "http://ott-loki:3100/loki/api/v1/label/service_name/values?start=${start}000000000&end=${now}000000000"
    if ($body) {
        $svcs = ($body | ConvertFrom-Json).data
        if ($svcs -contains 'ott-backend') { $found = $true; break }
    }
    if ($attempt -lt 3) { Start-Sleep -Seconds 10 }
}
if (-not $found) { throw "SECURITY INVARIANT FAILED: no ott-backend log lines reached loki in the last 15 minutes. Log shipping is down - alloy is not tailing, or its mounted config changed without the container being recreated. Nothing else in this deploy would have shown it." }
Write-Host '  alloy      : ott-backend logs arriving in loki'

# --- 3) Prometheus: are the targets actually being scraped? -----------------------
# The deploy already validates prometheus.yml and SIGHUPs it, which proves the file parses -
# not that anything answers. A renamed service or a moved port passes promtool and leaves
# every dashboard empty.
$tgtRaw = Invoke-Probe -ProbeId $probe -Url 'http://127.0.0.1:9090/api/v1/targets?state=active'
if (-not $tgtRaw) { throw 'SECURITY INVARIANT FAILED: prometheus did not answer its targets API' }
$targets = ($tgtRaw | ConvertFrom-Json).data.activeTargets
if (-not $targets) { throw 'SECURITY INVARIANT FAILED: prometheus reports no active scrape targets - it is running but collecting nothing' }
$down = @($targets | Where-Object { $_.health -ne 'up' })
if ($down.Count -gt 0) {
    $detail = ($down | ForEach-Object { "$($_.scrapeUrl)=$($_.health)" }) -join ', '
    throw "SECURITY INVARIANT FAILED: prometheus has $($down.Count) target(s) not up ($detail). Metrics for those instances are missing."
}
Write-Host "  prometheus : $($targets.Count) scrape target(s), all up"

# --- 4) Grafana: is it up? ---------------------------------------------------------
# Only reachability and its database, which is what /api/health reports without
# credentials. NOT verified here: that the provisioning tree was re-read - that needs an
# authenticated API call, and putting the admin password in a deploy check to learn it
# would be a worse trade than the gap. Stated so nobody reads this line as more than it is.
$healthRaw = Invoke-Probe -ProbeId $probe -Url 'http://ott-grafana:3000/api/health'
if (-not $healthRaw) { throw 'SECURITY INVARIANT FAILED: grafana did not answer /api/health' }
$health = $healthRaw | ConvertFrom-Json
if ($health.database -ne 'ok') { throw "SECURITY INVARIANT FAILED: grafana reports database '$($health.database)' - dashboards and alert rules are not being served" }
Write-Host "  grafana    : healthy (v$($health.version))"
