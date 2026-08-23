// Blocks bare `docker compose up` and `docker run` from the Bash/PowerShell tools.
//
// Why: a compose up without docker-compose.netlock.yml does NOT include the egress
// lock, silently reopening the frontend's internet access - the exact defense added
// after the 2026-06 XMRig compromise. Deploys must go through deploy-rolling.ps1
// (or deploy.ps1 for a deliberate single-instance rollback), which pin the file set.
//
// Allowed: any compose command carrying netlock.yml, the dev overlay
// (docker-compose.dev.yml), the E2E stack (docker-compose.e2e.yml), and every
// non-up subcommand (ps/logs/exec/down/build).
//
// Why e2e.yml is on the list: it carries the egress lock itself rather than taking it
// from an overlay - its default network is `internal: true`, so the frontend has no
// internet, which is the invariant this hook exists to protect. It also runs under its
// own project name (-p ott-e2e) with ott-e2e-* container names and only 127.0.0.1:8080
// published, so it cannot replace or reach the production stack.
// If that file ever stops locking egress, take it off this list.

const DEPLOY_HINT =
  'Use .\\deploy-rolling.ps1 (or .\\deploy.ps1 for a deliberate single-instance rollback). ' +
  'For the dev stack: docker compose -f docker-compose.yml -f docker-compose.dev.yml up';

let raw = '';
process.stdin.on('data', (d) => (raw += d));
process.stdin.on('end', () => {
  let cmd = '';
  try {
    cmd = JSON.parse(raw).tool_input?.command ?? '';
  } catch {
    process.exit(0);
  }

  // Drop heredoc bodies before analysing: commit messages and file content piped
  // this way are prose, not commands, and a message line that happens to start
  // with "docker run" must not be treated as an invocation.
  const noHeredoc = cmd.replace(
    /<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1[\s\S]*?^\t*\2\s*$/gm,
    '<<HEREDOC'
  );

  // join shell line continuations (PowerShell backtick, bash backslash) first
  const flat = noHeredoc.replace(/`\r?\n/g, ' ').replace(/\\\r?\n/g, ' ');
  const segments = flat.split(/[\n;]|&&|\|\||\|/).map((s) => s.trim());

  let reason = null;
  for (const s of segments) {
    // require a real argument boundary after the subcommand, so prose like
    // "docker run) when ..." is not mistaken for an invocation
    if (/^docker\s+run(\s|$)/.test(s)) {
      reason =
        'Blocked: `docker run` starts a container outside the compose networks, ' +
        'bypassing the egress lock and the app/data network split. ' +
        'If this one-off is deliberate, run it yourself in a terminal.';
      break;
    }
    if (/^docker(\s+|-)compose\b/.test(s) && /\bup(\s|$)/.test(s)) {
      if (
        /netlock/.test(s) ||
        /docker-compose\.dev\.yml/.test(s) ||
        /docker-compose\.e2e\.yml/.test(s)
      )
        continue;
      reason =
        'Blocked: this compose up has no docker-compose.netlock.yml, so it would ' +
        'deploy with frontend egress OPEN. ' +
        DEPLOY_HINT;
      break;
    }
  }

  if (!reason) process.exit(0);

  console.log(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        permissionDecision: 'deny',
        permissionDecisionReason: reason,
      },
    })
  );
});
