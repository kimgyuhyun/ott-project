# Claude Code hooks for this repo

Two kinds live here. A **mirror** only reports; a **leash** refuses to let something run.
Something gets a leash only when finding out afterwards is too late to fix it.

`block-bare-compose.js` (leash, PreToolUse) denies `docker compose up` without
`docker-compose.netlock.yml` (and any `docker run`) when Claude Code tries to run it. See
the header comment in the script for why. Deploys must go through `deploy-rolling.ps1` /
`deploy.ps1`.

`backend-test-mirror.js` (mirror, Stop) runs `./gradlew testFast` when a turn ends and
hands any failures back to Claude, so a red test is caught in seconds instead of ten
minutes later in CI. It stops the turn only for a failing test or code that does not
compile. Every infrastructure problem - no `JAVA_HOME`, no Docker, a timeout, another
instance already running - passes silently, because a mirror that blocks on its own
plumbing gets switched off.

`testFast` is every test except the 13 Testcontainers-backed classes, which carry
`@Tag("testcontainers")`: 28s against 104s for the full suite, the difference being one
PostgreSQL container per class. The `test` task and CI still run all of them. Tag any new
Testcontainers test the same way, or it lands in the fast set and drags the 28s up with it.

## Registering it on a new machine

Nothing to do - `settings.json` next to this file is committed, so a `pull` is enough.

That only holds because sessions are opened **at the repo root** (`C:\solo-project\ott-project`).
Claude Code reads project settings from the directory the session starts in and does not
look into subdirectories, so a session opened one level up at `C:\solo-project` loads
nothing from here and every hook is silently dead. If you must work from the parent
directory, the registration has to be duplicated there by hand and will not travel
between machines.

Machine-specific values belong in `.claude/settings.local.json`, which is gitignored.
Never put them in `settings.json` or in a hook script. The test mirror needs one:

```json
{ "env": { "JAVA_HOME": "C:\\Users\\USER\\.jdks\\liberica-21.0.7" } }
```

Without it the mirror just passes silently - there is no `java` on PATH on this machine.

Restart the session after changing `settings.json` - the settings watcher only picks up
directories that already had a settings file when the session started.

## Verifying it works

Run `docker run --rm hello-world` through Claude. It should be denied. That probe is
safe even if the hook is dead - worst case a 13 KB image prints a greeting. Never probe
with a real `docker compose up`: if the hook is not loaded, that actually deploys with
frontend egress open.

For the test mirror, break one assertion in a non-Testcontainers test and end a turn: it
must come back red. A mirror that has never been seen going red is indistinguishable from
one that is dead - the same reason `test` in `build.gradle` carries its `useJUnitPlatform`
warning. Its state file and lock live in `.git/claude-backend-test-mirror.*`; deleting
them just forces the next run.
