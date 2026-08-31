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
    '-f', 'docker-compose.ha.yml',
    # Monitoring (prometheus/grafana/loki) MUST be in this file set. Without it the
    # `--remove-orphans` below treats those containers as orphans and deletes them
    # on every deploy. Including it keeps them alive (and started in step 1).
    '-f', 'docker-compose.monitoring.yml'
)

# Backend instances, in replacement order.
$Instances = @('ott-app', 'ott-app-2')

# [SECURITY 2026-08-07] This used to poll a per-instance loopback port (8090/8093).
# That stopped working when the backends left the egress network: a container attached
# ONLY to internal networks cannot publish a host port (PLATFORM 3절), so `docker port
# ott-app` is now empty and every poll fails - which is what aborted the first deploy
# after the change.
# We ask nginx instead, addressing the instance by CONTAINER NAME rather than through the
# upstream. That keeps the property the loopback ports existed for: we still know WHICH
# instance answered, so sequential replacement stays controllable. (cd.yml already probed
# the backend this exact way.)
function Wait-Healthy {
    param([string]$Name, [int]$TimeoutSec = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $body = ''
        # A backend that is not listening yet makes wget write to stderr, which is a
        # terminating error under $ErrorActionPreference='Stop' - hence the try/catch.
        try { $body = "$(docker exec ott-nginx wget -qO- -T3 "http://${Name}:8090/actuator/health" 2>$null)" } catch { $body = '' }
        if ($body -match '"status"\s*:\s*"UP"') {
            Write-Host "  $Name is healthy"
            return
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
Write-Host '=== Updating non-backend services (incl. monitoring) ==='
# The two egress proxies are in this step on purpose: they must be listening before the
# backends come up, because the backends have no other way out.
docker compose @ComposeFiles up -d --remove-orphans --no-deps postgres redis kafka rabbitmq frontend nginx loki prometheus grafana alloy ott-egress-proxy ott-smtp-proxy
if ($LASTEXITCODE -ne 0) { throw 'docker compose up (non-backend) failed' }

# --- 2. Backend instances, one at a time --------------------------------------
foreach ($name in $Instances) {
    $svc = if ($name -eq 'ott-app') { 'app' } else { 'app2' }

    Write-Host "=== Replacing $name (service: $svc) ==="
    docker compose @ComposeFiles up -d --force-recreate --no-deps $svc
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed for $svc" }

    Wait-Healthy -Name $name
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

# [SECURITY 2026-08-07] Backend outbound is now proxy-only with a destination allow-list.
# The old check here asserted the OPPOSITE (that the backend could resolve external hosts
# directly); leaving it in would abort every deploy the moment app leaves the egress network.
# The expectation is inverted, not the command - see check 4.
#
# All four are judged on POSITIVE signals (exit code / 403 / a connection that succeeds),
# never on "the output was empty" - see the note in cd.yml about why.
# A missing 'proxy' network on ott-app-2 (ha.yml is not covered by extends) is the easiest
# mistake to make here, and check 1 is what catches it.
Write-Host '=== VERIFY backend outbound is proxy-only and allow-listed ==='
foreach ($name in $Instances) {
    # 1. An allow-listed destination must go through the proxy.
    docker exec $name curl -s -o /dev/null --max-time 8 -x http://ott-egress-proxy:3128 https://api.iamport.kr/ | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "OUTBOUND FAILED: $name cannot reach an allow-listed destination through the proxy (curl exit $LASTEXITCODE). OAuth/payment/mail/TMDB are down." }
    Write-Host "  $name -> proxy -> api.iamport.kr: OK"

    # 2. A destination that is NOT on the list must be refused BY THE PROXY.
    #    %{http_connect} is the proxy's answer to CONNECT (%{http_code} stays 000 when the
    #    tunnel is never established, so it cannot tell a refusal from a broken probe).
    $denied = "$(docker exec $name curl -s -o /dev/null -w '%{http_connect}' --max-time 8 -x http://ott-egress-proxy:3128 https://1.1.1.1/)".Trim()
    if ($denied -match '^2') { throw "SECURITY INVARIANT FAILED: $name reached a non-allow-listed destination through the proxy (CONNECT -> $denied)" }
    if ($denied -ne '403') { throw "allow-list probe returned an unexpected result on $name (CONNECT -> '$denied') - not treating it as denied" }
    Write-Host "  $name -> proxy -> 1.1.1.1: DENIED (403)"

    # 3. Direct outbound (bypassing the proxy) must fail - the app is off the egress network.
    #    Two things are deliberate here. bash prints the verdict itself instead of us
    #    reading an exit code, and stderr is discarded INSIDE the container (2>/dev/null
    #    on the inner bash). A failed /dev/tcp writes "connect: Network is unreachable" to
    #    stderr, and native stderr under $ErrorActionPreference='Stop' is a terminating
    #    error no matter how it is redirected on the host side - so the noise has to be
    #    stopped at the source.
    $direct = ''
    try { $direct = (docker exec $name bash -c "timeout 3 bash -c 'echo > /dev/tcp/1.1.1.1/443' 2>/dev/null && echo DIRECT-OPEN || echo DIRECT-BLOCKED" | Out-String).Trim() }
    catch { $direct = "PROBE-ERROR: $($_.Exception.Message)" }
    if ($direct -match 'DIRECT-OPEN') { throw "SECURITY INVARIANT FAILED: $name has direct outbound (1.1.1.1:443 reachable without the proxy)" }
    if ($direct -notmatch 'DIRECT-BLOCKED') { throw "direct-outbound probe returned an unexpected result on $name ('$direct') - not treating it as blocked" }
    Write-Host "  $name direct 1.1.1.1:443: BLOCKED"

    # 4. External DNS must fail too. Same command as the pre-2026-08-07 check, opposite
    #    expectation: on an internal-only network the container cannot resolve public names,
    #    which is why name resolution has to happen inside the proxy.
    $dns = docker exec $name sh -c "getent hosts oauth2.googleapis.com > /dev/null && echo RESOLVED || echo BLOCKED"
    if ("$dns" -match 'RESOLVED') { throw "SECURITY INVARIANT FAILED: $name still resolves external DNS (is it still on the egress network?)" }
    Write-Host "  $name external DNS: BLOCKED"

    # 5. The SMTP path over SOCKS, both halves. Checks 1-4 only exercise squid, so without
    #    this a misrouted or mis-ruled sockd passes the deploy and only shows up when a
    #    user waits for a verification mail that never arrives. It is also the only check
    #    that covers the sockd.conf "external:" address / compose ipv4_address pairing.
    #    It has already earned its keep twice: a CRLF config that would not parse, and an
    #    outbound bound to the wrong interface (sockd logged "running" and its port was
    #    open both times - only this check saw that no session ever got out).
    #
    #    This curl build has no smtp:// or telnet:// (only file ftp ftps http https ipfs
    #    ipns), so we speak to port 587 as if it were HTTP and let --http0.9 hand us the
    #    server's greeting as the body. Reaching a real 220 banner proves the whole path:
    #    SOCKS rule, DNS inside the proxy, and the egress interface.
    #    Cost of the trick: curl sends one GET line that the mail server answers with
    #    "502 Unrecognized command". Harmless, and it is the price of a positive signal.
    $smtpProbe = 'curl -s --http0.9 --max-time 10 --socks5-hostname ott-smtp-proxy:1080 http://smtp.naver.com:587/ 2>/dev/null'
    $smtp = "$(docker exec $name bash -c $smtpProbe)".Trim()
    if ($smtp -notmatch '220\s+smtp\.naver\.com') { throw "OUTBOUND FAILED: $name cannot reach SMTP through the SOCKS proxy (got '$smtp'). Signup and email verification are down." }
    Write-Host "  $name -> socks -> smtp.naver.com:587: OK (220 banner)"

    #    And the deny half: exit code 97 is curl's "the SOCKS proxy refused", which is a
    #    different code from any ordinary connection failure - so it says the ruleset
    #    rejected us rather than the network being broken.
    $smtpDenyProbe = 'curl -s -o /dev/null -w "%{exitcode}" --max-time 10 --socks5-hostname ott-smtp-proxy:1080 http://1.1.1.1:443/ 2>/dev/null'
    $smtpDeny = "$(docker exec $name bash -c $smtpDenyProbe)".Trim()
    if ($smtpDeny -eq '0') { throw "SECURITY INVARIANT FAILED: $name reached a non-allow-listed destination through the SOCKS proxy" }
    if ($smtpDeny -ne '97') { throw "SOCKS allow-list probe returned an unexpected result on $name (curl exit '$smtpDeny') - not treating it as denied" }
    Write-Host "  $name -> socks -> 1.1.1.1: DENIED (ruleset)"
}

# [SECURITY 2026-08-29] Container hardening (PLATFORM 2절). Every check above is about the
# network; nothing here ever looked at no-new-privileges / capabilities / limits /
# read-only rootfs / tmpfs flags after a deploy. The script explains why, and is shared
# with deploy.ps1 so the two cannot drift.
& .\security\check-container-hardening.ps1 -ComposeFiles $ComposeFiles

# --- 4. Apply any nginx config change -----------------------------------------
# The conf is a single-file bind mount, so compose sees no change when only its
# CONTENTS change and step 1 leaves nginx running the config it parsed at startup.
# A reload makes it re-read the file without dropping connections - unlike a
# recreate, which costs a ~1s connection refusal.
# cd.yml runs these same two lines after calling this script; having them here too
# means a MANUAL deploy applies conf changes as well. Reloading twice is harmless.
Write-Host '=== Reloading nginx config ==='
docker exec ott-nginx nginx -t
if ($LASTEXITCODE -ne 0) { throw 'nginx -t failed - config NOT reloaded, nginx still serving the previous one' }
docker exec ott-nginx nginx -s reload
if ($LASTEXITCODE -ne 0) { throw 'nginx -s reload failed' }

# --- 5. Apply any Prometheus alert-rule change --------------------------------
# Same trap as nginx: monitoring/rules is a bind mount, so step 1 does not
# recreate prometheus when only the rule FILES change, and the running process
# keeps the rules it parsed at startup. SIGHUP makes it re-read them (it is a
# signal, not a stop - the container keeps running and the TSDB is untouched).
# Check before reloading: prometheus REJECTS a bad reload and silently keeps the
# old rules, so a broken file would otherwise look like a successful deploy.
# Note what the check cannot tell you: an empty rules directory is valid config,
# so a passing check does not prove the rules were loaded. Confirm that with
#   curl -s http://127.0.0.1:9090/api/v1/rules
Write-Host '=== Reloading Prometheus alert rules ==='
docker exec ott-prometheus promtool check config /etc/prometheus/prometheus.yml
if ($LASTEXITCODE -ne 0) { throw 'promtool check config failed - rules NOT reloaded, prometheus still evaluating the previous ones' }
docker kill -s HUP ott-prometheus | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'prometheus SIGHUP failed - alert rules were not reloaded' }

# --- 6. Read-only smoke through nginx -----------------------------------------
# Wait-Healthy above reaches the backend BY CONTAINER NAME from inside nginx, so it never
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
# (Same block as deploy.ps1.)
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

Write-Host '=== ROLLING DEPLOY OK ==='

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
