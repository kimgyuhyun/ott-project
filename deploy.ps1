# deploy.ps1 - canonical SECURE local deploy (single source of truth)
#
# Why this exists:
#   A bare `docker compose up` does NOT include the egress lock (netlock).
#   That silently reopens the frontend's internet access - the exact defense
#   added after the 2026-06 XMRig compromise. This script pins the correct
#   file set so the security invariants are never dropped by mistake.
#
# Prerequisites (manual local deploy):
#   - .env must already exist (decrypt from .env.enc with SOPS+age first).
#   - Local clean images tagged ott-backend:clean / ott-frontend:clean must
#     exist, OR set $env:APP_IMAGE / $env:FRONT_IMAGE to a ghcr digest.
#
# For the DEV stack instead (hot reload, egress OPEN - never expose publicly):
#   docker compose -f docker-compose.yml -f docker-compose.dev.yml up

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path .env)) {
    throw '.env not found. Decrypt it from .env.enc (SOPS+age) before deploying.'
}

# This script deploys the SINGLE-instance layout. Since 2026-07-20 the site runs
# two backends behind an nginx upstream, and this file set does not include the ha
# overlay - running it would drop nginx back to the single-backend config and
# --remove-orphans would delete ott-app-2, all without saying so.
# Use deploy-rolling.ps1 instead. This one stays for deliberate rollbacks.
if (docker ps -q -f name=ott-app-2) {
    throw 'ott-app-2 is running: this script would revert the site to a single instance. Use .\deploy-rolling.ps1. To roll back on purpose, remove ott-app-2 first (docker rm -f ott-app-2), then rerun this.'
}

Write-Host '=== SECURE DEPLOY (prod + egress-locked) ==='
# Monitoring is included so `--remove-orphans` does not delete prometheus/grafana/loki.
docker compose `
    -f docker-compose.yml `
    -f docker-compose.prod.yml `
    -f docker-compose.netlock.yml `
    -f docker-compose.monitoring.yml `
    up -d --remove-orphans
if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }

# Security invariant: frontend must NOT reach the internet.
Write-Host '=== VERIFY frontend egress is blocked (expected: BLOCKED) ==='
$egress = docker exec ott-frontend node -e "const s=require('net').connect({host:'1.1.1.1',port:443,timeout:3500});s.on('connect',()=>{console.log('REACHABLE');process.exit()});s.on('timeout',()=>{console.log('BLOCKED');process.exit()});s.on('error',()=>{console.log('BLOCKED');process.exit()})"
if ("$egress" -match 'REACHABLE') { throw 'SECURITY INVARIANT FAILED: frontend egress is NOT blocked' }
Write-Host "frontend egress: $egress"

# Security invariant (2026-07-18 network segmentation): frontend must NOT reach
# the data tier (postgres/redis live on the app-only 'data' network). If this is
# REACHABLE the segmentation regressed and a compromised frontend could pivot to the DB.
Write-Host '=== VERIFY frontend cannot reach data tier (expected: BLOCKED) ==='
$lateral = docker exec ott-frontend node -e "const s=require('net').connect({host:'ott-postgres',port:5432,timeout:3500});s.on('connect',()=>{console.log('REACHABLE');process.exit()});s.on('timeout',()=>{console.log('BLOCKED');process.exit()});s.on('error',()=>{console.log('BLOCKED');process.exit()})"
if ("$lateral" -match 'REACHABLE') { throw 'SECURITY INVARIANT FAILED: frontend can reach postgres (data tier not isolated)' }
Write-Host "frontend -> postgres: $lateral"

# [SECURITY 2026-08-07] Backend outbound is proxy-only with a destination allow-list.
# These four are the same invariants deploy-rolling.ps1 checks; this script had no backend
# check at all, so without them a rollback deploy would drop the whole invariant silently.
# Single instance here (the ha overlay is not in this file set), so ott-app only.
# All four judge on positive signals - exit code, 403, a connection that actually succeeds.
Write-Host '=== VERIFY backend outbound is proxy-only and allow-listed ==='

# 1. An allow-listed destination must go through the proxy.
docker exec ott-app curl -s -o /dev/null --max-time 8 -x http://ott-egress-proxy:3128 https://api.iamport.kr/ | Out-Null
if ($LASTEXITCODE -ne 0) { throw "OUTBOUND FAILED: ott-app cannot reach an allow-listed destination through the proxy (curl exit $LASTEXITCODE). OAuth/payment/mail/TMDB are down." }
Write-Host '  ott-app -> proxy -> api.iamport.kr: OK'

# 2. A destination that is NOT on the list must be refused BY THE PROXY.
#    %{http_connect} is the proxy's answer to CONNECT; %{http_code} stays 000 when the
#    tunnel is never established and so cannot tell a refusal from a broken probe.
$denied = "$(docker exec ott-app curl -s -o /dev/null -w '%{http_connect}' --max-time 8 -x http://ott-egress-proxy:3128 https://1.1.1.1/)".Trim()
if ($denied -match '^2') { throw "SECURITY INVARIANT FAILED: ott-app reached a non-allow-listed destination through the proxy (CONNECT -> $denied)" }
if ($denied -ne '403') { throw "allow-list probe returned an unexpected result (CONNECT -> '$denied') - not treating it as denied" }
Write-Host '  ott-app -> proxy -> 1.1.1.1: DENIED (403)'

# 3. Direct outbound (bypassing the proxy) must fail - the app is off the egress network.
#    bash prints the verdict itself, and stderr is discarded INSIDE the container: a failed
#    /dev/tcp writes "connect: Network is unreachable", and native stderr under
#    $ErrorActionPreference='Stop' is a terminating error however it is redirected on the
#    host side, so the noise has to be stopped at the source.
$direct = ''
try { $direct = (docker exec ott-app bash -c "timeout 3 bash -c 'echo > /dev/tcp/1.1.1.1/443' 2>/dev/null && echo DIRECT-OPEN || echo DIRECT-BLOCKED" | Out-String).Trim() }
catch { $direct = "PROBE-ERROR: $($_.Exception.Message)" }
if ($direct -match 'DIRECT-OPEN') { throw 'SECURITY INVARIANT FAILED: ott-app has direct outbound (1.1.1.1:443 reachable without the proxy)' }
if ($direct -notmatch 'DIRECT-BLOCKED') { throw "direct-outbound probe returned an unexpected result ('$direct') - not treating it as blocked" }
Write-Host '  ott-app direct 1.1.1.1:443: BLOCKED'

# 4. External DNS must fail too - name resolution belongs to the proxy now.
$dns = docker exec ott-app sh -c "getent hosts oauth2.googleapis.com > /dev/null && echo RESOLVED || echo BLOCKED"
if ("$dns" -match 'RESOLVED') { throw 'SECURITY INVARIANT FAILED: ott-app still resolves external DNS (is it still on the egress network?)' }
Write-Host '  ott-app external DNS: BLOCKED'

# 5. The SMTP path over SOCKS, both halves. Checks 1-4 only exercise squid, so a misrouted
#    or mis-ruled sockd would pass the deploy and surface as verification mails that never
#    arrive. This curl build has no smtp://, so we talk to 587 as HTTP and let --http0.9
#    hand us the greeting; a real 220 banner proves the SOCKS rule, DNS inside the proxy
#    and the egress interface at once. It costs one GET line the mail server rejects.
$smtpProbe = 'curl -s --http0.9 --max-time 10 --socks5-hostname ott-smtp-proxy:1080 http://smtp.naver.com:587/ 2>/dev/null'
$smtp = "$(docker exec ott-app bash -c $smtpProbe)".Trim()
if ($smtp -notmatch '220\s+smtp\.naver\.com') { throw "OUTBOUND FAILED: ott-app cannot reach SMTP through the SOCKS proxy (got '$smtp'). Signup and email verification are down." }
Write-Host '  ott-app -> socks -> smtp.naver.com:587: OK (220 banner)'

#    Deny half. curl exit 97 is specifically "the SOCKS proxy refused", distinct from an
#    ordinary connection failure, so it says the ruleset rejected us.
$smtpDenyProbe = 'curl -s -o /dev/null -w "%{exitcode}" --max-time 10 --socks5-hostname ott-smtp-proxy:1080 http://1.1.1.1:443/ 2>/dev/null'
$smtpDeny = "$(docker exec ott-app bash -c $smtpDenyProbe)".Trim()
if ($smtpDeny -eq '0') { throw 'SECURITY INVARIANT FAILED: ott-app reached a non-allow-listed destination through the SOCKS proxy' }
if ($smtpDeny -ne '97') { throw "SOCKS allow-list probe returned an unexpected result (curl exit '$smtpDeny') - not treating it as denied" }
Write-Host '  ott-app -> socks -> 1.1.1.1: DENIED (ruleset)'

# [SECURITY 2026-08-29] Container hardening (PLATFORM 2절), same script as
# deploy-rolling.ps1, so the checks cannot drift between the two deploy paths.
# The file set must match the one deployed above - this script is the SINGLE-instance
# layout, so no ha overlay here.
& .\security\check-container-hardening.ps1 -ComposeFiles @(
    '-f', 'docker-compose.yml',
    '-f', 'docker-compose.prod.yml',
    '-f', 'docker-compose.netlock.yml',
    '-f', 'docker-compose.monitoring.yml'
)

# Apply any nginx config change.
# The conf is a single-file bind mount, so the `up -d` above does not recreate
# nginx when only its CONTENTS change, and the running process keeps the config it
# parsed at startup. A reload re-reads the file with no dropped connections.
# (Same two lines as deploy-rolling.ps1 and cd.yml.)
Write-Host '=== Reloading nginx config ==='
docker exec ott-nginx nginx -t
if ($LASTEXITCODE -ne 0) { throw 'nginx -t failed - config NOT reloaded, nginx still serving the previous one' }
docker exec ott-nginx nginx -s reload
if ($LASTEXITCODE -ne 0) { throw 'nginx -s reload failed' }

# Apply any Prometheus alert-rule change (same two lines as deploy-rolling.ps1).
# The rules are a bind mount, so the `up -d` above does not re-read them; SIGHUP
# does, without stopping the container. Check first - a rejected reload leaves
# the OLD rules running and says nothing.
Write-Host '=== Reloading Prometheus alert rules ==='
docker exec ott-prometheus promtool check config /etc/prometheus/prometheus.yml
if ($LASTEXITCODE -ne 0) { throw 'promtool check config failed - rules NOT reloaded, prometheus still evaluating the previous ones' }
docker kill -s HUP ott-prometheus | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'prometheus SIGHUP failed - alert rules were not reloaded' }

# Read-only smoke through nginx (same block as deploy-rolling.ps1).
# The health wait above reaches the backend BY CONTAINER NAME from inside nginx, so it never
# exercises nginx's own server/location blocks. A broken proxy_pass or a wrong server_name
# passes every check so far and shows up only as a dead site. These three probes enter the
# way a browser does: TLS, SNI, routing, upstream, security filters.
#
# GET only - no login, no account, no writes. That is what makes it safe to run against
# production on every deploy, and it is why loadtest/main.js is NOT used here: that one
# needs the 500 seeded accounts from seed-users.sql and writes progress rows.
#
# --resolve pins the public name to loopback so the request never leaves the host while
# still sending laputa.kozow.com as SNI. The SNI matters: the 443 default_server has
# ssl_reject_handshake on, so a missing or wrong SNI is refused during the handshake and
# would read as an outage even when the site is fine.
#
# Judged on the exact expected code, never on "no error" - same rule as the checks above.
# Runs last, after both reloads, so it tests the config that is actually serving.
Write-Host '=== VERIFY public entry points through nginx (read-only) ==='

# 1. Frontend: location / -> ott-frontend:3000
$smokeFront = "$(docker exec ott-nginx curl -s -o /dev/null -w '%{http_code}' --max-time 10 --resolve laputa.kozow.com:443:127.0.0.1 https://laputa.kozow.com/)".Trim()
if ($smokeFront -ne '200') { throw "SMOKE FAILED: GET / returned '$smokeFront' (expected 200) - nginx routing or the frontend is down" }
Write-Host '  GET /             -> 200'

# 2. API: location /api/ -> ott-app:8090. The byte count is what proves the query ran -
#    a 200 with an empty body would mean the route works but the list came back empty.
$smokeApi = "$(docker exec ott-nginx curl -s -o /dev/null -w '%{http_code}:%{size_download}' --max-time 10 --resolve laputa.kozow.com:443:127.0.0.1 https://laputa.kozow.com/api/anime)".Trim()
if ($smokeApi -notmatch '^200:') { throw "SMOKE FAILED: GET /api/anime returned '$smokeApi' (expected 200:<bytes>) - nginx -> backend routing is broken" }
if ([int]($smokeApi -split ':')[1] -lt 100) { throw "SMOKE FAILED: GET /api/anime returned '$smokeApi' - a 200 with no list, so the query or the data is gone" }
Write-Host "  GET /api/anime    -> $smokeApi (code:bytes)"

# 3. Security filter chain: an anonymous call to an authenticated endpoint must be refused.
#    A 200 here is a hole, not a pass.
$smokeAuth = "$(docker exec ott-nginx curl -s -o /dev/null -w '%{http_code}' --max-time 10 --resolve laputa.kozow.com:443:127.0.0.1 https://laputa.kozow.com/api/users/me)".Trim()
if ($smokeAuth -ne '401') { throw "SMOKE FAILED: anonymous GET /api/users/me returned '$smokeAuth' (expected 401) - the security filter chain is not refusing anonymous access" }
Write-Host '  GET /api/users/me -> 401 (anonymous refused)'

Write-Host '=== DEPLOY OK ==='
