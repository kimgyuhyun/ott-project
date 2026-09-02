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

# Set by the broker watermark check below, asserted after the loop. Without it that
# check disappears the moment the container is renamed or dropped from the file set -
# the same silent pass it exists to prevent. rabbitmq is in the base compose file, so
# every deploy path has it; if that ever stops being true this is meant to fail and be
# updated deliberately, not to quietly stop running.
$brokerWatermarkChecked = $false

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
    # --- RabbitMQ memory watermark: the other half of the mem_limit pair ----------
    # The Memory check above is only half of what rabbitmq/10-memory.conf calls a pair.
    # mem_limit decides when the kernel SIGKILLs the container; the broker's own
    # watermark decides when it blocks publishers so it never gets there. Set one
    # without the other and the broker OOMs instead of applying back-pressure - on a
    # dunning retry queue that means dropping exactly the payment retries that caused
    # the spike. Only the kernel half was checked until now.
    #
    # Why this cannot ride on `up -d`: the broker reads conf.d ONCE at boot, and compose
    # recreates a container when the service DEFINITION changes, never when the CONTENT
    # of a bind-mounted file does. An edited value reaches disk on the next deploy
    # (cd.yml copies it to C:\ott-deploy\config) while the running broker keeps the old
    # one, with nothing saying so. Measured 2026-09-02: the 05:17 deploy rewrote the
    # conf and recreated ott-app, and ott-rabbitmq stayed the container built on 08-30.
    #
    # Shape follows squid/sockd in deploy-rolling.ps1, which have the same problem and
    # solve it by asserting the OUTCOME rather than by forcing a recreate. Forcing a
    # recreate here would restart the broker mid-rolling-deploy, the one thing that
    # script exists to avoid. So: read what is mounted now, ask the broker what it
    # actually booted on, and require the two to agree.
    if ($name -eq 'ott-rabbitmq') {
        $brokerWatermarkChecked = $true

        # Read the conf from INSIDE the container, not from the repo. The repo file is
        # two copies upstream of what the broker sees (repo -> C:\ott-deploy\config via
        # cd.yml -> mount), and a manual deploy.ps1 run does not refresh that copy at
        # all, so the repo file can differ from the mount for legitimate reasons. What
        # is mounted now is what the next boot will use, and that is what has to match.
        $confLines = docker exec $name cat /etc/rabbitmq/conf.d/10-memory.conf
        if ($LASTEXITCODE -ne 0) { throw "SECURITY INVARIANT FAILED: $name - cannot read the mounted /etc/rabbitmq/conf.d/10-memory.conf, so the watermark cannot be verified" }

        $setting = @($confLines | Where-Object { $_ -notmatch '^\s*#' -and $_ -match 'vm_memory_high_watermark' })
        if ($setting.Count -ne 1) { throw "SECURITY INVARIANT FAILED: $name - expected exactly one vm_memory_high_watermark line in the mounted conf, found $($setting.Count)" }

        # .relative is not a variant of the setting, it is the bug the file was written
        # to fix. This broker cannot read its cgroup ceiling, so a relative watermark is
        # taken against HOST memory - measured get_total_memory() = 16671514624 on a 1g
        # container, putting the default 0.4 at ~6.2GiB, far above where the kernel kills.
        if ($setting[0] -match 'vm_memory_high_watermark\.relative') {
            throw "SECURITY INVARIANT FAILED: $name uses a RELATIVE memory watermark ($($setting[0].Trim())). This broker reads host memory, not its cgroup ceiling, so a relative watermark lands above mem_limit and the kernel kills it before flow control ever fires. Use vm_memory_high_watermark.absolute."
        }

        # Deliberately narrow: MiB and GiB only, case-sensitive. rabbitmq's own parser is
        # quirkier than it looks - measured against
        # rabbit_resource_monitor_misc:parse_information_unit/1 on 3.13.7:
        #   600MiB -> 629145600   600Mi -> 629145600   1GiB -> 1073741824
        #   600MB  -> 600000000   600M  -> 600000000   (powers of 10, not 2)
        #   1024kib -> 1048576    but 1024KiB CRASHES it with a case_clause
        # Reproducing that table by hand is how a check ends up confidently agreeing with
        # the wrong number, so anything outside the two verified forms stops the deploy.
        # Widening this is meant to be a deliberate edit, not a silent pass.
        if ($setting[0] -cnotmatch '^\s*vm_memory_high_watermark\.absolute\s*=\s*(\d+)(MiB|GiB)\s*$') {
            throw "SECURITY INVARIANT FAILED: $name has a watermark this check will not interpret: '$($setting[0].Trim())'. Only <integer>MiB and <integer>GiB are verified against rabbitmq's parser - if you meant to change units, widen the check in the same commit."
        }
        $confBytes = [int64]$Matches[1] * $(if ($Matches[2] -eq 'GiB') { 1073741824 } else { 1048576 })

        # -t bounds the wait: rabbitmqctl blocks against a booting node, and a deploy
        # check that hangs is worse than one that fails. No `2>&1` on a native command -
        # in PS 5.1 that wraps stderr in an ErrorRecord and flips $? even on exit 0.
        $runtime = docker exec $name rabbitmqctl -q -t 10 eval 'vm_memory_monitor:get_memory_limit().'
        if ($LASTEXITCODE -ne 0) { throw "SECURITY INVARIANT FAILED: $name - rabbitmqctl could not report the effective memory limit" }
        $runtimeStr = "$($runtime | Select-Object -First 1)".Trim()
        if ($runtimeStr -notmatch '^\d+$') { throw "SECURITY INVARIANT FAILED: $name - unexpected rabbitmqctl output for the memory limit: '$runtimeStr'" }
        $runtimeBytes = [int64]$runtimeStr

        # The drift check. Remediation is deliberately in the message: the value IS
        # settable at runtime (contrary to the conf's note that a recreate is required),
        # so the broker can be corrected with no downtime and the container recreated at
        # the next window to make it survive a restart.
        if ($runtimeBytes -ne $confBytes) {
            throw "SECURITY INVARIANT FAILED: $name booted on a memory watermark of $runtimeBytes B but the mounted conf now says $confBytes B ($($setting[0].Trim())). conf.d is read once at boot and compose does not recreate on a content change, so this container is running a stale value. Fix now without downtime: docker exec $name rabbitmqctl set_vm_memory_high_watermark absolute $($Matches[1])$($Matches[2]) - then recreate the container at the next window so it survives a restart."
        }

        # The pairing, in the direction that matters. Above the ceiling, flow control can
        # never fire before the kernel kills the broker - the watermark is decoration.
        if ($confBytes -ge $hc.Memory) {
            throw "SECURITY INVARIANT FAILED: $name watermark ($confBytes B) is at or above its mem_limit ($($hc.Memory) B). The kernel would SIGKILL the broker before it ever blocked a publisher."
        }
        # Headroom, not just ordering. rabbitmq/10-memory.conf sets the value at 60% of
        # mem_limit and states what the remaining 40% is for: BEAM's own overhead plus
        # the window between crossing the watermark and flow control actually taking
        # effect. 0.75 is the point where more than half of that stated headroom is gone
        # - a bound derived from the conf's own rationale, not a measured threshold.
        if ($confBytes -gt [int64]($hc.Memory * 0.75)) {
            throw "SECURITY INVARIANT FAILED: $name watermark ($confBytes B) leaves too little headroom under mem_limit ($($hc.Memory) B). 10-memory.conf budgets ~40% for BEAM overhead and flow-control settling time; this eats more than half of it."
        }

        Write-Host "  $name : memory watermark $confBytes B verified live, under mem_limit $($hc.Memory) B"
    }

    Write-Host "  $name : hardening OK"
}

# The broker check is the only per-container one here, so it is the only one that can
# vanish without the loop noticing. See the flag's declaration above.
if (-not $brokerWatermarkChecked) { throw 'SECURITY INVARIANT FAILED: the RabbitMQ memory watermark check never ran - ott-rabbitmq was not among the deployed containers. It is in the base compose file, so this means the stack changed and the check needs updating, not skipping.' }
