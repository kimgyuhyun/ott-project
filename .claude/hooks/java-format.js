// Formats Java that Claude Code just edited (PostToolUse).
//
// Why here and not only in CI: spotless is configured with ratchetFrom 'origin/main',
// and on a push straight to main the CI checkout IS origin/main, so nothing differs and
// nothing gets checked. The gate that actually holds on that path is the local one, and
// this hook makes it hold without anyone remembering to run spotlessApply.
//
// It fixes rather than reports, so it never blocks an edit. The only thing it hands back
// is a note that the file on disk changed, because Claude's copy of it is now stale and
// the next edit would be built on text that no longer exists.
//
// Every infrastructure problem - no JAVA_HOME, no gradlew, a busy daemon, a timeout -
// passes silently, for the same reason the test mirror does it: a hook that blocks on
// its own plumbing gets switched off.

const { execFileSync } = require('child_process');
const { createHash } = require('crypto');
const fs = require('fs');
const path = require('path');

const TIMEOUT_MS = 90_000;

const hash = (p) => {
  try {
    return createHash('sha256').update(fs.readFileSync(p)).digest('hex');
  } catch {
    return null;
  }
};

let raw = '';
process.stdin.on('data', (d) => (raw += d));
process.stdin.on('end', () => {
  let file = '';
  try {
    const input = JSON.parse(raw);
    if (!/^(Edit|Write|MultiEdit)$/.test(input.tool_name || '')) process.exit(0);
    file = input.tool_input?.file_path || '';
  } catch {
    process.exit(0);
  }

  // Only Java under the backend source tree. Generated Q classes live in build/ and are
  // outside the source set, so spotless never sees them anyway.
  const norm = file.replace(/\\/g, '/');
  if (!norm.endsWith('.java') || !norm.includes('/backend/src/')) process.exit(0);

  const root = process.env.CLAUDE_PROJECT_DIR || process.cwd();
  const backend = path.join(root, 'backend');
  const isWindows = process.platform === 'win32';
  const gradlew = path.join(backend, isWindows ? 'gradlew.bat' : 'gradlew');
  if (!fs.existsSync(gradlew)) process.exit(0);

  const before = hash(file);
  if (before === null) process.exit(0);

  try {
    // A .bat is not an executable image, so Windows needs a shell to run it - without
    // one this fails with EINVAL and the hook goes quiet while formatting nothing.
    // The path is quoted because shell:true would otherwise split it on spaces.
    execFileSync(
      isWindows ? `"${gradlew}"` : gradlew,
      ['spotlessApply', '--quiet', '--console=plain'],
      { cwd: backend, timeout: TIMEOUT_MS, stdio: 'ignore', shell: isWindows }
    );
  } catch {
    process.exit(0); // no JAVA_HOME, daemon contention, timeout - all silent
  }

  if (hash(file) === before) process.exit(0);

  // Exit 2 is what feeds stderr back to Claude. Nothing was blocked - the edit already
  // landed - but the file has to be re-read before it is edited again.
  const shown = path.relative(root, file).replace(/\\/g, '/');
  process.stderr.write(
    `spotlessApply reformatted ${shown}. Re-read it before editing it again; ` +
      `your copy is stale.\n`
  );
  process.exit(2);
});
