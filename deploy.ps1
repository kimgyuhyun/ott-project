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

Write-Host '=== SECURE DEPLOY (prod + egress-locked) ==='
docker compose `
    -f docker-compose.yml `
    -f docker-compose.prod.yml `
    -f docker-compose.netlock.yml `
    up -d --remove-orphans
if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }

# Security invariant: frontend must NOT reach the internet.
Write-Host '=== VERIFY frontend egress is blocked (expected: BLOCKED) ==='
$egress = docker exec ott-frontend node -e "const s=require('net').connect({host:'1.1.1.1',port:443,timeout:3500});s.on('connect',()=>{console.log('REACHABLE');process.exit()});s.on('timeout',()=>{console.log('BLOCKED');process.exit()});s.on('error',()=>{console.log('BLOCKED');process.exit()})"
if ("$egress" -match 'REACHABLE') { throw 'SECURITY INVARIANT FAILED: frontend egress is NOT blocked' }
Write-Host "frontend egress: $egress"
Write-Host '=== DEPLOY OK ==='
