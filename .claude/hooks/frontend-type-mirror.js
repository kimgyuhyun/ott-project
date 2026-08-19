// Stop hook: type-checks the frontend at end of turn and feeds errors back.
//
// The backend gets this for free - testFast has to compile before it can run anything, so
// a broken build stops the turn. The frontend has no tests, so nothing ever compiles it
// until CI runs `next build`, and a type error sits there for the whole session. This is
// that missing half: `tsc --noEmit`, measured at 3.9s on the kgh98 machine.
//
// Mirror, not a leash for infrastructure: no node_modules, no tsc, a timeout, another
// instance already running - all of those exit 0 and say nothing. The only thing that
// stops the turn is a type error, which is exactly what CI would reject later.
//
// It re-runs only when frontend sources changed since the last clean run. Unlike gradle,
// tsc has no up-to-date check that would make an unchanged re-run cheap - it costs the
// same 3.9s every time - so the skip has to happen here, before tsc is started.

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const TIMEOUT_MS = 120_000;
const LOCK_STALE_MS = 10 * 60_000;
const MAX_REPORTED = 5;

function pass() {
  process.exit(0);
}

let raw = '';
process.stdin.on('data', (d) => (raw += d));
process.stdin.on('end', () => {
  let input = {};
  try {
    input = JSON.parse(raw.replace(/^﻿/, ''));
  } catch {
    pass();
  }

  // The turn was already extended once by this hook. Extending again on the same user
  // message is how a Stop hook loops forever.
  if (input.stop_hook_active) pass();

  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd;
  if (!projectDir) pass();

  const frontendDir = path.join(projectDir, 'frontend');
  const gitDir = path.join(projectDir, '.git');
  if (!fs.existsSync(frontendDir) || !fs.existsSync(gitDir)) pass();

  // Nothing under frontend changed since the last clean run - the previous result holds.
  const statePath = path.join(gitDir, 'claude-frontend-type-mirror.json');
  const fingerprint = fingerprintFrontend(frontendDir);
  if (fingerprint && readState(statePath) === fingerprint) pass();

  const tsc = path.join(frontendDir, 'node_modules', 'typescript', 'bin', 'tsc');
  if (!fs.existsSync(tsc)) pass();

  const lockPath = path.join(gitDir, 'claude-frontend-type-mirror.lock');
  if (!acquireLock(lockPath)) pass();

  // Nothing in here may call process.exit: that skips the finally and leaves the lock
  // behind, which would mute the hook for everyone until the lock goes stale.
  let verdict = null;
  try {
    verdict = runTypeCheck(frontendDir, tsc, statePath, fingerprint);
  } finally {
    try {
      fs.unlinkSync(lockPath);
    } catch {
      /* already gone */
    }
  }

  if (verdict) {
    process.stderr.write(verdict + '\n');
    process.exit(2);
  }
  pass();
});

function runTypeCheck(frontendDir, tsc, statePath, fingerprint) {
  // The bin is run through node rather than npx: npx is a .cmd on Windows, which
  // spawnSync cannot execute without a shell, and it would fail silently.
  const run = spawnSync(process.execPath, [tsc, '--noEmit'], {
    cwd: frontendDir,
    timeout: TIMEOUT_MS,
    encoding: 'utf8',
  });

  // Killed by the timeout, or never started at all: infrastructure, so stay quiet.
  if (run.error || run.signal) return null;

  const output = ((run.stdout || '') + (run.stderr || '')).trim();

  if (run.status === 0) {
    try {
      if (fingerprint) fs.writeFileSync(statePath, fingerprint);
    } catch {
      /* the state file is an optimisation, not a requirement */
    }
    return null;
  }

  // A non-zero exit with nothing that looks like a diagnostic is a broken tsconfig or a
  // bad invocation, not the code under test. Reporting it would train the reader to
  // ignore this hook.
  const errors = output.split(/\r?\n/).filter((l) => /error TS\d+/.test(l));
  if (errors.length === 0) return null;

  const shown = errors.slice(0, MAX_REPORTED);
  const rest = errors.length - shown.length;
  return (
    `tsc --noEmit failed with ${errors.length} type error(s):\n` +
    shown.map((l) => '  ' + l).join('\n') +
    (rest > 0 ? `\n  ... and ${rest} more` : '') +
    '\nFix these before finishing; CI runs the same check inside next build.'
  );
}

// Same shape as the backend mirror's fingerprint: path, size and mtime of every source
// file, plus the files that change what tsc does at all.
function fingerprintFrontend(frontendDir) {
  const h = crypto.createHash('sha256');
  const walk = (dir) => {
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return false;
    }
    for (const e of entries.sort((a, b) => (a.name < b.name ? -1 : 1))) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (!walk(p)) return false;
      } else if (e.isFile()) {
        try {
          const s = fs.statSync(p);
          h.update(`${p}|${s.size}|${s.mtimeMs}\n`);
        } catch {
          return false;
        }
      }
    }
    return true;
  };
  if (!walk(path.join(frontendDir, 'src'))) return null;
  for (const name of ['tsconfig.json', 'package.json', 'next.config.ts']) {
    try {
      const s = fs.statSync(path.join(frontendDir, name));
      h.update(`${name}|${s.size}|${s.mtimeMs}\n`);
    } catch {
      return null;
    }
  }
  return h.digest('hex');
}

function readState(statePath) {
  try {
    return fs.readFileSync(statePath, 'utf8').trim();
  } catch {
    return null;
  }
}

// Exclusive create, so two Claude instances on the same checkout cannot run tsc at once.
// A lock left behind by a killed process would otherwise disable the hook permanently.
function acquireLock(lockPath) {
  try {
    fs.writeFileSync(lockPath, String(process.pid), { flag: 'wx' });
    return true;
  } catch (e) {
    if (e.code === 'EEXIST') {
      try {
        if (Date.now() - fs.statSync(lockPath).mtimeMs > LOCK_STALE_MS) {
          fs.writeFileSync(lockPath, String(process.pid));
          return true;
        }
      } catch {
        /* vanished between the two calls */
      }
    }
    return false;
  }
}
