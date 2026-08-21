// Formats what Claude Code just edited (PostToolUse).
//
// Java goes through spotless, everything under frontend/ goes through prettier. Both are
// deterministic and auto-fixing, so there is nothing to decide and nothing to report -
// the file is simply correct afterwards.
//
// Why it exists at all, when CI already checks both: not to plug a gap. Spotless used
// to run with ratchetFrom 'origin/main', which checked zero files on a push straight to
// main - the hook was the only gate on that path. That ratchet is gone and CI now checks
// every file on every push, so this hook is purely a convenience: it fixes formatting on
// the spot instead of letting it come back as a red CI over whitespace. Same reason
// prettier runs here.
//
// It never blocks an edit. The only thing it hands back is a note that the file on disk
// changed, because Claude's copy is then stale and the next edit would be built on text
// that no longer exists.
//
// Every infrastructure problem - no JAVA_HOME, no node_modules, a busy gradle daemon, a
// timeout - passes silently, for the same reason the test mirror does it: a hook that
// blocks on its own plumbing gets switched off.

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

// Returns a zero-arg runner for the file, or null when nothing here formats it.
const formatterFor = (norm, file, root) => {
  if (norm.endsWith('.java') && norm.includes('/backend/src/')) {
    // Generated Q classes live in build/ and are outside the source set, so spotless
    // never sees them anyway.
    const backend = path.join(root, 'backend');
    const isWindows = process.platform === 'win32';
    const gradlew = path.join(backend, isWindows ? 'gradlew.bat' : 'gradlew');
    if (!fs.existsSync(gradlew)) return null;
    return () =>
      // A .bat is not an executable image, so Windows needs a shell to run it - without
      // one this fails with EINVAL and the hook goes quiet while formatting nothing.
      // The path is quoted because shell:true would otherwise split it on spaces.
      execFileSync(
        isWindows ? `"${gradlew}"` : gradlew,
        ['spotlessApply', '--quiet', '--console=plain'],
        { cwd: backend, timeout: TIMEOUT_MS, stdio: 'ignore', shell: isWindows }
      );
  }

  if (norm.includes('/frontend/') && !norm.includes('/node_modules/')) {
    const frontend = path.join(root, 'frontend');
    const bin = path.join(frontend, 'node_modules', 'prettier', 'bin', 'prettier.cjs');
    if (!fs.existsSync(bin)) return null;
    // Run the bin through node rather than npx: npx is a .cmd on Windows (same EINVAL
    // trap as gradlew.bat) and resolving it costs more than the format itself.
    // --ignore-unknown drops file types prettier has no parser for, and .prettierignore
    // is honoured because the cwd is frontend/.
    const rel = path.relative(frontend, file);
    return () =>
      execFileSync(process.execPath, [bin, '--write', '--ignore-unknown', rel], {
        cwd: frontend,
        timeout: TIMEOUT_MS,
        stdio: 'ignore',
      });
  }

  return null;
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
  if (!file) process.exit(0);

  const root = process.env.CLAUDE_PROJECT_DIR || process.cwd();
  const norm = file.replace(/\\/g, '/');
  const run = formatterFor(norm, file, root);
  if (!run) process.exit(0);

  const before = hash(file);
  if (before === null) process.exit(0);

  try {
    run();
  } catch {
    process.exit(0); // missing toolchain, contention, timeout - all silent
  }

  if (hash(file) === before) process.exit(0);

  // Exit 2 is what feeds stderr back to Claude. Nothing was blocked - the edit already
  // landed - but the file has to be re-read before it is edited again.
  const shown = path.relative(root, file).replace(/\\/g, '/');
  process.stderr.write(
    `Reformatted ${shown}. Re-read it before editing it again; your copy is stale.\n`
  );
  process.exit(2);
});
