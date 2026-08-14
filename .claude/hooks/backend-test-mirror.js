// Stop hook: runs the fast backend test subset at end of turn and feeds failures back.
//
// This is a mirror, not a leash for infrastructure: no JAVA_HOME, no Docker, a timeout,
// a missing results directory - all of those exit 0 and say nothing. The only things that
// stop the turn are a red test and code that does not compile, because those are the two
// states a later CI run would reject and both are cheap to fix while the context is warm.
//
// Scope is testFast (see backend/build.gradle), which skips the Testcontainers classes:
// 28s instead of 570s. CI still runs everything.

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

  const backendDir = path.join(projectDir, 'backend');
  const gitDir = path.join(projectDir, '.git');
  if (!fs.existsSync(backendDir) || !fs.existsSync(gitDir)) pass();

  // Nothing under backend changed since the last green run - the previous result still holds.
  const statePath = path.join(gitDir, 'claude-backend-test-mirror.json');
  const fingerprint = fingerprintBackend(backendDir);
  if (fingerprint && readState(statePath) === fingerprint) pass();

  // Gradle needs a JDK, and this machine has no java on PATH; JAVA_HOME comes from
  // .claude/settings.local.json because the path differs per machine.
  if (!process.env.JAVA_HOME) pass();

  const lockPath = path.join(gitDir, 'claude-backend-test-mirror.lock');
  if (!acquireLock(lockPath)) pass();

  // Nothing in here may call process.exit: that skips the finally and leaves the lock
  // behind, which would mute the hook for everyone until the lock goes stale.
  let verdict = null;
  try {
    verdict = runTests(backendDir, statePath, fingerprint);
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

// Returns a message to stop the turn with, or null to let it end.
function runTests(backendDir, statePath, fingerprint) {
  const gradlew = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew';
  const resultsDir = path.join(backendDir, 'build', 'test-results', 'testFast');
  const before = Date.now();
  const run = spawnSync(gradlew, ['testFast', '--console=plain'], {
    cwd: backendDir,
    timeout: TIMEOUT_MS,
    encoding: 'utf8',
    shell: true,
    maxBuffer: 32 * 1024 * 1024,
  });

  // Timed out, or gradle never started. Infrastructure, not code.
  if (run.error || run.signal) return null;

  const failures = collectFailures(resultsDir, before);

  if (failures === null) {
    // This run wrote no results. If gradle still succeeded it decided the tests were
    // up to date, which is green. If it failed, the one cause worth stopping on is
    // code that does not compile - that produces no XML at all, and it is exactly the
    // red light CI would raise ten minutes later.
    if (run.status !== 0) {
      const compileErrors = extractCompileErrors(
        (run.stdout || '') + (run.stderr || '')
      );
      if (compileErrors.length) {
        return (
          'backend testFast: the backend does not compile.\n' +
          compileErrors.slice(0, MAX_REPORTED).map((l) => '  ' + l).join('\n')
        );
      }
      return null; // gradle broke for some other reason - not the code's fault
    }
  } else if (failures.length) {
    const shown = failures.slice(0, MAX_REPORTED);
    const more =
      failures.length > shown.length
        ? `\n  ...and ${failures.length - shown.length} more`
        : '';
    return (
      `backend testFast: ${failures.length} failing test(s).\n` +
      shown.map((f) => `  ${f.name}\n    ${f.message}`).join('\n') +
      more
    );
  }

  // Green: remember the tree so the next turn skips the run if nothing changed.
  if (fingerprint) {
    try {
      fs.writeFileSync(statePath, fingerprint);
    } catch {
      /* the state file is an optimisation; losing it only costs a rerun */
    }
  }
  return null;
}

// Path + size + mtime of every file under backend/src, plus build.gradle. Content is not
// read: this only has to answer "did anything change", and hashing the tree must stay
// far cheaper than the test run it is guarding.
function fingerprintBackend(backendDir) {
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
  if (!walk(path.join(backendDir, 'src'))) return null;
  try {
    const s = fs.statSync(path.join(backendDir, 'build.gradle'));
    h.update(`build.gradle|${s.size}|${s.mtimeMs}\n`);
  } catch {
    return null;
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

// Exclusive create, so two Claude instances on the same checkout cannot run gradle at once.
// A lock left behind by a killed process would otherwise disable the hook permanently.
function acquireLock(lockPath) {
  try {
    fs.writeFileSync(lockPath, String(process.pid), { flag: 'wx' });
    return true;
  } catch {
    try {
      if (Date.now() - fs.statSync(lockPath).mtimeMs > LOCK_STALE_MS) {
        fs.writeFileSync(lockPath, String(process.pid));
        return true;
      }
    } catch {
      /* fall through */
    }
    return false;
  }
}

// Returns null when there are no usable results, [] when everything passed.
function collectFailures(resultsDir, notOlderThan) {
  let files;
  try {
    files = fs.readdirSync(resultsDir).filter((f) => f.endsWith('.xml'));
  } catch {
    return null;
  }
  if (!files.length) return null;

  const failures = [];
  let fresh = 0;
  for (const f of files) {
    const p = path.join(resultsDir, f);
    let xml;
    try {
      // Results left over from an earlier run would report failures that are already
      // fixed, so anything not written by this run is ignored.
      if (fs.statSync(p).mtimeMs < notOlderThan) continue;
      fresh++;
      xml = fs.readFileSync(p, 'utf8');
    } catch {
      continue;
    }
    // The attribute group must stay lazy. Greedy, it swallows the '/' of a self-closing
    // <testcase .../>, so the '>' branch matches instead and runs on to the next test's
    // </testcase> - pinning that test's failure on this earlier, passing one.
    const caseRe = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
    let m;
    while ((m = caseRe.exec(xml))) {
      const body = m[3] || '';
      const fail = /<(failure|error)\b([^>]*)(\/>|>)/.exec(body);
      if (!fail) continue;
      failures.push({
        name: `${attr(m[1], 'classname')} > ${attr(m[1], 'name')}`,
        message: summarise(unescapeXml(attr(fail[2], 'message') || fail[1])),
      });
    }
  }
  return fresh ? failures : null;
}

function attr(s, name) {
  // Anchored to a boundary so that asking for 'name' cannot match the tail of 'classname'.
  // Gradle happens to emit name first today, which is the only reason it has not bitten.
  const m = new RegExp(`(?:^|\\s)${name}="([^"]*)"`).exec(s || '');
  return m ? m[1] : '';
}

function unescapeXml(s) {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#10;/g, '\n')
    .replace(/&#13;/g, '')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

// Flattened to one line rather than truncated at the first newline: AssertJ puts the
// exception type on line one and the part worth reading ("expected X but was Y") after it.
function summarise(s) {
  const line = String(s).replace(/\s+/g, ' ').trim();
  return line.length > 200 ? line.slice(0, 200) + '...' : line;
}

// Deduplicated because gradle echoes javac's diagnostics on both stdout and stderr.
function extractCompileErrors(output) {
  const seen = new Set();
  for (const l of output.split(/\r?\n/)) {
    if (/\.java:\d+:\s*(error|오류):/.test(l)) seen.add(l.trim());
  }
  return [...seen];
}
