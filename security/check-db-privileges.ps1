# check-db-privileges.ps1 - PLATFORM 4절 (DB 계정 권한) invariants, checked against the
# running stack.
#
# Called by deploy.ps1 and deploy-rolling.ps1 after `up`, next to
# check-container-hardening.ps1. A separate file on purpose: that one declares itself as
# PLATFORM 2절 (container hardening) and this is a different rule, so keeping them apart
# means neither file's stated scope becomes a lie.
#
# Why it exists (2026-09-02):
#   The least-privilege runtime account has a silent fallback on BOTH sides.
#     docker-compose.prod.yml : SPRING_DATASOURCE_USERNAME: ${DB_APP_USERNAME:-root}
#     security/initdb/20-least-privilege.sh : exits 0 without creating the account when
#       DB_APP_USERNAME / DB_APP_PASSWORD are unset.
#   So one missing .env line silently puts the application on the postgres SUPERUSER:
#   the app starts, the smoke tests pass, the deploy goes green, and nothing anywhere
#   says the privilege boundary is gone.
#
#   Two things make that hard to notice later. initdb.d only runs when the data volume
#   is first created, so editing those scripts never touches a live cluster - the account
#   you think you fixed is not the account that is running. And restore-drill.ps1 does
#   check the ott_app role and its grants, but only inside a RESTORED cluster, never the
#   live one.
#
# Judged on positive signals, like the other checks: a role that exists and reports false
# for every escalation flag, no DDL rights, and live connections proving it is the account
# actually in use - never on "no error appeared". That last point matters more than usual
# here: psql exits 0 and prints an empty result for a role that does not exist, so an
# empty answer is a failure in this file, not a pass.
#
# Usage:
#   & .\security\check-db-privileges.ps1 -ComposeFiles $ComposeFiles
param(
    # The SAME file set the caller deployed with, for the same reason as the hardening
    # check: deploy-rolling.ps1 includes the ha overlay and deploy.ps1 does not.
    [Parameter(Mandatory = $true)][string[]]$ComposeFiles
)

$ErrorActionPreference = 'Stop'

Write-Host '=== VERIFY database account privileges (PLATFORM section 4) ==='

function Get-ContainerEnv {
    param([string]$Id, [string]$Key)
    # Read the env docker actually created the container with, not `printenv` inside it:
    # this is the compose-resolved value, which is exactly where the ${VAR:-root} fallback
    # becomes visible.
    $vars = (docker inspect $Id | Out-String | ConvertFrom-Json)[0].Config.Env
    foreach ($v in $vars) { if ($v.StartsWith("$Key=")) { return $v.Substring($Key.Length + 1) } }
    return $null
}

function Get-PgScalar {
    param([string]$Id, [string]$User, [string]$Db, [string]$Sql)
    $out = docker exec $Id psql -U $User -d $Db -tAc $Sql
    if ($LASTEXITCODE -ne 0) { throw "SECURITY INVARIANT FAILED: psql failed on the postgres container - the DB privilege invariants could not be checked. Query: $Sql" }
    return "$($out | Select-Object -First 1)".Trim()
}

$ids = docker compose @ComposeFiles ps -q
if (-not $ids) { throw 'db privilege check found no running containers - it must not pass by having nothing to look at' }

# Select by compose SERVICE label, not by container name. The ha overlay calls the second
# backend `app2` and deploy.ps1 does not deploy it at all, so matching on a name prefix
# would quietly cover a different set of instances from each of the two deploy paths.
$appIds = @()
$pgId   = $null
foreach ($id in $ids) {
    $insp = (docker inspect $id | Out-String | ConvertFrom-Json)[0]
    $svc  = $insp.Config.Labels.'com.docker.compose.service'
    if ($svc -match '^app\d*$')  { $appIds += $id }
    elseif ($svc -eq 'postgres') { $pgId = $id }
}
if (-not $pgId)          { throw 'SECURITY INVARIANT FAILED: no postgres container in the deployed file set - the DB privilege invariants could not be checked' }
if ($appIds.Count -eq 0) { throw 'SECURITY INVARIANT FAILED: no application container in the deployed file set - the DB privilege invariants could not be checked' }

$pgSuper = Get-ContainerEnv -Id $pgId -Key 'POSTGRES_USER'
$pgDb    = Get-ContainerEnv -Id $pgId -Key 'POSTGRES_DB'
if (-not $pgSuper -or -not $pgDb) { throw 'SECURITY INVARIANT FAILED: postgres container exposes no POSTGRES_USER/POSTGRES_DB - cannot determine which account is the superuser' }

# Every instance must agree. Checking only one would miss the case where a single backend
# was recreated with a stale env and is the one running as superuser.
$appUsers = @()
foreach ($id in $appIds) {
    $insp = (docker inspect $id | Out-String | ConvertFrom-Json)[0]
    $name = $insp.Name.TrimStart('/')
    $u    = Get-ContainerEnv -Id $id -Key 'SPRING_DATASOURCE_USERNAME'
    if (-not $u) { throw "SECURITY INVARIANT FAILED: $name has no SPRING_DATASOURCE_USERNAME - it would fall back to the datasource default rather than the least-privilege account" }
    if ($u -eq $pgSuper) { throw "SECURITY INVARIANT FAILED: $name connects to postgres as '$u', which is the SUPERUSER account. This is the `${DB_APP_USERNAME:-root} fallback in docker-compose.prod.yml firing because DB_APP_USERNAME is missing from .env - the application is running with unrestricted database rights." }
    $appUsers += $u
}
$distinct = @($appUsers | Sort-Object -Unique)
if ($distinct.Count -ne 1) { throw "SECURITY INVARIANT FAILED: application instances disagree on the database account ($($appUsers -join ', ')) - one of them was created with a different env" }
$appUser = $distinct[0]

# The name is ours, out of our own compose file, but it is about to be pasted into SQL as
# a literal. Validate rather than find out the assumption was wrong.
if ($appUser -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw "SECURITY INVARIANT FAILED: refusing to query with an unexpected database role name '$appUser'" }

# One query, one exact expected answer. Any escalation flag being true fails, and the
# message carries what was actually found. An empty result means the role does not exist -
# which is what a skipped 20-least-privilege.sh leaves behind.
$flags = Get-PgScalar -Id $pgId -User $pgSuper -Db $pgDb -Sql "select rolsuper::text||' '||rolcreatedb::text||' '||rolcreaterole::text||' '||rolbypassrls::text from pg_roles where rolname='$appUser';"
if (-not $flags)                        { throw "SECURITY INVARIANT FAILED: the application connects as '$appUser' but no such role exists in postgres. security/initdb/20-least-privilege.sh only runs when the data volume is first created, so it cannot have created it on this cluster." }
if ($flags -ne 'false false false false') { throw "SECURITY INVARIANT FAILED: role '$appUser' has escalated privileges (rolsuper rolcreatedb rolcreaterole rolbypassrls = $flags). Expected all false." }

# DDL separation. Flyway runs migrations as the superuser on purpose (SPRING_FLYWAY_USER
# in docker-compose.prod.yml), so the runtime account must not be able to change the
# schema. Without this, an account can be non-superuser and still own everything.
$ddl = Get-PgScalar -Id $pgId -User $pgSuper -Db $pgDb -Sql "select has_schema_privilege('$appUser','public','CREATE')::text;"
if ($ddl -ne 'false') { throw "SECURITY INVARIANT FAILED: role '$appUser' can CREATE in schema public (got '$ddl') - the runtime account has DDL rights it should not have." }

# Configured is not the same as used. This is what separates "the env var looks right"
# from "the application is actually on that account".
$conns = Get-PgScalar -Id $pgId -User $pgSuper -Db $pgDb -Sql "select count(*) from pg_stat_activity where datname='$pgDb' and usename='$appUser';"
if ($conns -notmatch '^\d+$' -or [int]$conns -lt 1) { throw "SECURITY INVARIANT FAILED: no live connection to '$pgDb' from role '$appUser' (got '$conns'). The instances are configured for it but something else is serving the traffic." }

Write-Host "  $($appIds.Count) app instance(s) on role '$appUser' : not superuser, no DDL, $conns live connection(s)"
