# deploy-drain.ps1 - take one backend out of the nginx upstream before replacing it.
# Dot-sourced by deploy-rolling.ps1, and called by loadtest/keepalive/rolling.sh (DRAIN=1)
# when it measures the procedure, so the deploy and the measurement run the same code.
# It finds servers by the line format `server <name>:8090 ...;` in the upstream block -
# changing that format (port, one line per server) makes it throw "not found".
#
# Why: without this, nginx only learns an instance is gone when a request to it fails, and
# then keeps it out for fail_timeout (5s) after the LAST failure. The deploy script checks
# health directly and moved on to the next instance immediately, so nginx could still be
# excluding the fresh instance when the other one went down -> "no live upstreams" 502s.
# Measured 2026-10-09: 51 / 0 / 0 failed requests in three runs at 30 r/s
# (loadtest/keepalive/README.md). Marking the instance `down` and reloading stops new
# requests BEFORE it stops, and the reload also clears nginx's failure state, so the
# fresh instance is a candidate again the moment it is un-drained.

# Rewrites the upstream server lines in $ConfPath (the file nginx has bind-mounted) so that
# only $Drain is marked `down`, then validates and reloads. $Drain = '' restores all servers.
function Set-UpstreamDrain {
    param(
        [Parameter(Mandatory)][string]$ConfPath,
        [Parameter(Mandatory)][string]$NginxContainer,
        [string]$Drain = ''
    )
    $text = [IO.File]::ReadAllText($ConfPath)
    # Clear any earlier drain first, including one left behind by an aborted deploy.
    $text = $text -replace '(?m)^(\s*server\s+\S+:8090\b[^;]*?)\s+down;', '$1;'
    if ($Drain) {
        # \b after the port keeps 'ott-app' from matching 'ott-app-2'.
        $pattern = "(?m)^(\s*server\s+$([regex]::Escape($Drain)):8090\b[^;]*);"
        if ($text -notmatch $pattern) { throw "upstream server '$Drain' not found in $ConfPath" }
        $text = $text -replace $pattern, '$1 down;'
    }
    # BOM-less UTF-8, same as cd.yml's config export. nginx rejects a leading BOM.
    [IO.File]::WriteAllText($ConfPath, $text)

    docker exec $NginxContainer nginx -t
    if ($LASTEXITCODE -ne 0) { throw "nginx -t failed after setting drain='$Drain' - not reloaded" }
    docker exec $NginxContainer nginx -s reload
    if ($LASTEXITCODE -ne 0) { throw "nginx -s reload failed after setting drain='$Drain'" }
    # The reload is asynchronous: the master starts new workers and lets the old ones finish
    # their in-flight requests. Give the swap a moment before the caller stops the instance.
    Start-Sleep -Seconds 1
}
