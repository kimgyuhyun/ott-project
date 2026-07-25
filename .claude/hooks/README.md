# Claude Code hooks for this repo

`block-bare-compose.js` denies `docker compose up` without `docker-compose.netlock.yml`
(and any `docker run`) when Claude Code tries to run it. See the header comment in the
script for why. Deploys must go through `deploy-rolling.ps1` / `deploy.ps1`.

## Registering it on a new machine

The script travels with this repo, but the hook **registration** does not: sessions are
opened with `C:\solo-project` as the working directory, which is one level above this
repo and therefore not version-controlled.

So on a new machine, create `C:\solo-project\.claude\settings.json` with:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash|PowerShell",
        "hooks": [
          {
            "type": "command",
            "shell": "bash",
            "command": "node \"${CLAUDE_PROJECT_DIR:-.}/ott-project/.claude/hooks/block-bare-compose.js\"",
            "timeout": 10,
            "statusMessage": "checking docker command"
          }
        ]
      }
    ]
  }
}
```

If sessions are opened at the repo root instead, drop the `ott-project/` segment from
the command path and put the file in this repo's `.claude/settings.json`.

Restart the session (or open `/hooks`) after creating the file - the settings watcher
only picks up directories that already had a settings file when the session started.

## Verifying it works

Run `docker run --rm hello-world` through Claude. It should be denied. That probe is
safe even if the hook is dead - worst case a 13 KB image prints a greeting. Never probe
with a real `docker compose up`: if the hook is not loaded, that actually deploys with
frontend egress open.
