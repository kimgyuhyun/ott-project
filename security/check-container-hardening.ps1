# check-container-hardening.ps1 - PLATFORM 2절 invariants, checked against running containers.
#
# Called by deploy.ps1 and deploy-rolling.ps1 after `up`. Kept in one file so the two
# deploy paths cannot drift apart on what they enforce.
#
# Why it exists (2026-08-29):
#   Every security check in the deploy scripts was about the network. Nothing looked at
#   whether no-new-privileges / capabilities / limits / read-only rootfs / tmpfs flags
#   still held after a deploy. A review then found two comment blocks asserting a tmpfs
#   behaviour docker does not have - that listing size= or mode= REPLACES the default
#   noexec,nosuid,nodev. It does not: docker always prepends those three and appends
#   whatever compose lists, so the only way to lose noexec is to write `exec` yourself.
#   Measured on docker 28.4.0; the table is in docker-compose.prod.yml beside the app
#   /tmp mount. Both wrong comments rode through two commits because no check ever
#   disagreed with them. Now one does.
#
# Judged on positive signals, like the network checks: an explicit flag present, a
# ceiling above zero. Never on "the output looked fine".
#
# Usage:
#   & .\security\check-container-hardening.ps1 -ComposeFiles $ComposeFiles
param(
    # The SAME file set the caller deployed with. Passed in rather than hardcoded because
    # deploy-rolling.ps1 includes the ha overlay and deploy.ps1 does not - hardcoding it
    # here would silently check the wrong stack from one of them.
    [Parameter(Mandatory = $true)][string[]]$ComposeFiles
)

$ErrorActionPreference = 'Stop'

# This file used to carry a debt list of containers still allowed a writable root
# filesystem (postgres, redis, kafka, rabbitmq, nginx, prometheus, loki, grafana).
# All eight were closed on 2026-08-30 - each one's write paths were measured with
# docker diff against a 2-week-old container and reopened as tmpfs - so the list is
# gone and read_only is now required of every container, with no exceptions.
# Do not reintroduce a list. If a new service cannot run read-only, measure it and
# open the paths it actually writes.

Write-Host '=== VERIFY container hardening (PLATFORM section 2) ==='

# Scope note: this only sees containers in the caller's file set. The opt-in stacks
# (pgadmin, certbot, e2e, multi) are not covered - they are not up during a deploy.
$ids = docker compose @ComposeFiles ps -q
if (-not $ids) { throw 'container hardening check found no running containers - it must not pass by having nothing to look at' }

foreach ($id in $ids) {
    # docker inspect returns a 1-element array across many lines; Out-String first or
    # ConvertFrom-Json sees one line at a time and fails.
    $insp = (docker inspect $id | Out-String | ConvertFrom-Json)[0]
    $name = $insp.Name.TrimStart('/')
    $hc   = $insp.HostConfig

    if (-not ($hc.SecurityOpt -contains 'no-new-privileges:true')) { throw "SECURITY INVARIANT FAILED: $name is missing no-new-privileges" }
    if (-not ($hc.CapDrop -contains 'ALL'))                        { throw "SECURITY INVARIANT FAILED: $name does not drop ALL capabilities" }
    if ($hc.Memory -le 0 -or $hc.NanoCpus -le 0)                   { throw "SECURITY INVARIANT FAILED: $name has no resource ceiling (mem=$($hc.Memory) nanocpus=$($hc.NanoCpus))" }
    if (-not $hc.ReadonlyRootfs) { throw "SECURITY INVARIANT FAILED: $name has a writable root filesystem" }

    # Tmpfs. The three flags are docker's default and adding size=/mode= does not drop
    # them, so requiring them spelled out is about the rule staying decidable from the
    # config (PLATFORM preamble) - and it is what catches a literal `exec`, the one edit
    # that really does open the mount.
    if ($hc.Tmpfs) {
        foreach ($path in $hc.Tmpfs.PSObject.Properties.Name) {
            $opts = $hc.Tmpfs.$path -split ','
            if ($opts -contains 'exec') { throw "SECURITY INVARIANT FAILED: $name mounts tmpfs $path with exec - a writable AND executable path is exactly how the 2026-06 XMRig landed" }
            foreach ($flag in @('noexec', 'nosuid', 'nodev')) {
                if ($opts -notcontains $flag) { throw "SECURITY INVARIANT FAILED: $name tmpfs $path does not spell out $flag" }
            }
        }
    }
    Write-Host "  $name : hardening OK"
}
