# deploy-rolling.ps1 - zero-downtime deploy for the 2-instance backend (ott-app + ott-app-2)
#
# Why this exists:
#   Running two instances does NOT give zero downtime on its own. A plain
#   `docker compose up -d` recreates BOTH backends at the same time, so the site
#   is down for the whole Spring boot window.
#   Measured on the 2026-07-20 experiment stack:
#     simultaneous restart : 197 requests -> 33 OK, 164x 502  (~22s outage)
#     rolling  restart     : 374 requests -> 374 OK, 0 failures
#   This script replaces one instance at a time and waits for it to answer
#   before touching the other one.
#
# Prerequisites:
#   - .env must exist (decrypt from .env.enc with SOPS+age first).
#   - Images must exist locally, OR set $env:APP_IMAGE / $env:FRONT_IMAGE.
#   - First-time switch to the 2-instance layout: run deploy.ps1 style full up
#     once with the ha overlay (see FIRST RUN below), then use this script.
#
# DB migration caveat (important):
#   During a rolling deploy the OLD and NEW versions run against the SAME database
#   for ~30 seconds. Migrations that DROP or RENAME a column will break the old
#   instance while it is still serving. For those changes either use the
#   expand/contract pattern, or accept the ~22s outage and deploy both at once.
#   Adding tables / nullable columns / NOT NULL-with-DEFAULT columns is safe.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$ComposeFiles = @(
    '-f', 'docker-compose.yml',
    '-f', 'docker-compose.prod.yml',
    '-f', 'docker-compose.netlock.yml',
    '-f', 'docker-compose.ha.yml'
)

# Instance name -> loopback port used to confirm it is actually up.
# Going through nginx would not tell us WHICH instance answered.
$Instances = [ordered]@{
    'ott-app'   = 8090
    'ott-app-2' = 8093
}

function Wait-Healthy {
    param([string]$Name, [int]$Port, [int]$TimeoutSec = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health" `
                                   -TimeoutSec 3 -UseBasicParsing
            if ($r.StatusCode -eq 200) {
                Write-Host "  $Name is healthy"
                return
            }
        } catch {
            # not up yet - keep polling
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become healthy within $TimeoutSec seconds. Deploy aborted - the other instance is still serving."
}

if (-not (Test-Path .env)) {
    throw '.env not found. Decrypt it from .env.enc (SOPS+age) before deploying.'
}

# --- 1. Non-backend services first (frontend/nginx/datastores) -----------------
# These are single-instance; recreating them is unrelated to backend rolling.
#
# --no-deps is REQUIRED here. nginx and frontend declare depends_on: app, so
# without it compose pulls `app` into this step and recreates it while ott-app-2
# may not exist yet - then step 2 recreates it a second time. That mistake cost
# a real ~15s outage on the 2026-07-20 first deploy (364 failed requests).
Write-Host '=== Updating non-backend services ==='
docker compose @ComposeFiles up -d --remove-orphans --no-deps postgres redis kafka rabbitmq frontend nginx
if ($LASTEXITCODE -ne 0) { throw 'docker compose up (non-backend) failed' }

# --- 2. Backend instances, one at a time --------------------------------------
foreach ($name in $Instances.Keys) {
    $port = $Instances[$name]
    $svc  = if ($name -eq 'ott-app') { 'app' } else { 'app2' }

    Write-Host "=== Replacing $name (service: $svc) ==="
    docker compose @ComposeFiles up -d --force-recreate --no-deps $svc
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed for $svc" }

    Wait-Healthy -Name $name -Port $port
}

# --- 3. Security invariants (same checks as deploy.ps1) -----------------------
Write-Host '=== VERIFY frontend egress is blocked (expected: BLOCKED) ==='
$egress = docker exec ott-frontend node -e "const s=require('net').connect({host:'1.1.1.1',port:443,timeout:3500});s.on('connect',()=>{console.log('REACHABLE');process.exit()});s.on('timeout',()=>{console.log('BLOCKED');process.exit()});s.on('error',()=>{console.log('BLOCKED');process.exit()})"
if ("$egress" -match 'REACHABLE') { throw 'SECURITY INVARIANT FAILED: frontend egress is NOT blocked' }
Write-Host "frontend egress: $egress"

Write-Host '=== VERIFY frontend cannot reach data tier (expected: BLOCKED) ==='
$lateral = docker exec ott-frontend node -e "const s=require('net').connect({host:'ott-postgres',port:5432,timeout:3500});s.on('connect',()=>{console.log('REACHABLE');process.exit()});s.on('timeout',()=>{console.log('BLOCKED');process.exit()});s.on('error',()=>{console.log('BLOCKED');process.exit()})"
if ("$lateral" -match 'REACHABLE') { throw 'SECURITY INVARIANT FAILED: frontend can reach postgres (data tier not isolated)' }
Write-Host "frontend -> postgres: $lateral"

# Both backends must have outbound internet (OAuth / mail / payment / TMDB).
# A missing 'egress' network on ott-app-2 is the easiest mistake to make here.
Write-Host '=== VERIFY both backends have egress ==='
foreach ($name in $Instances.Keys) {
    $dns = docker exec $name sh -c "getent hosts oauth2.googleapis.com > /dev/null && echo OK || echo FAIL"
    if ("$dns" -match 'FAIL') { throw "SECURITY/CONFIG FAILED: $name cannot resolve external hosts (egress network missing?)" }
    Write-Host "  $name egress: $dns"
}

Write-Host '=== ROLLING DEPLOY OK ==='
Write-Host 'Note: if the nginx config itself changed, nginx was recreated above and'
Write-Host '      that is a brief connection refusal no rolling can avoid.'
Write-Host '      Backend-only deploys (the normal case) leave nginx untouched.'

# FIRST RUN (switching from 1 instance to 2):
#   Just run this script. Tested 2026-07-20: nginx starts fine with the upstream
#   config even while ott-app-2 does not exist yet - requests that get routed to
#   the missing instance fail the connection and proxy_next_upstream immediately
#   retries them on ott-app, so no request is lost. Once ott-app-2 comes up it
#   joins the rotation on its own.
#
# ROLLBACK to the single-instance layout:
#   docker rm -f ott-app-2
#   .\deploy.ps1        # ha overlay omitted -> nginx.prod.conf (no upstream) again
