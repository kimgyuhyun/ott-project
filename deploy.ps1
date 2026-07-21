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
Write-Host '=== DEPLOY OK ==='
