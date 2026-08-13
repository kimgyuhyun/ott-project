# Claude Code hooks for this repo

`block-bare-compose.js` denies `docker compose up` without `docker-compose.netlock.yml`
(and any `docker run`) when Claude Code tries to run it. See the header comment in the
script for why. Deploys must go through `deploy-rolling.ps1` / `deploy.ps1`.

## Registering it on a new machine

Nothing to do - `settings.json` next to this file is committed, so a `pull` is enough.

That only holds because sessions are opened **at the repo root** (`C:\solo-project\ott-project`).
Claude Code reads project settings from the directory the session starts in and does not
look into subdirectories, so a session opened one level up at `C:\solo-project` loads
nothing from here and every hook is silently dead. If you must work from the parent
directory, the registration has to be duplicated there by hand and will not travel
between machines.

Machine-specific values (`JAVA_HOME` and the like) belong in `.claude/settings.local.json`,
which is gitignored. Never put them in `settings.json` or in a hook script.

Restart the session after changing `settings.json` - the settings watcher only picks up
directories that already had a settings file when the session started.

## Verifying it works

Run `docker run --rm hello-world` through Claude. It should be denied. That probe is
safe even if the hook is dead - worst case a 13 KB image prints a greeting. Never probe
with a real `docker compose up`: if the hook is not loaded, that actually deploys with
frontend egress open.
