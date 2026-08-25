/**
 * Derives the per-navigation asset budget from a browser trace.
 *
 * Counts only files the browser fetched from the server for the first time in the session — cache hits and repeats
 * are not load, and a file already downloaded is already in the simulated student's browser cache too.
 */
import fs from 'node:fs';

const rows = fs
  .readFileSync(process.argv[2], 'utf8')
  .split('\n')
  .filter(Boolean)
  .map(l => JSON.parse(l));

const isApi = r => {
  try {
    const p = new URL(r.url).pathname;
    return p.startsWith('/api') || p.startsWith('/management') || p.startsWith('/websocket');
  } catch {
    return false;
  }
};

// Steps before the app has rendered anything are the shell; each step after it is one navigation.
const SHELL_STEPS = /^(boot|01-|01b-|02a-|02b-)/;
// "work on" and "idle" steps are not navigations - the student is sitting on a view already loaded.
const NAVIGATION_STEPS = /^(03-|04-|05-|06-|07-|11-)/;

const seen = new Set();
const perStep = new Map();
let shell = 0;

for (const r of rows) {
  if (r.kind !== 'http' || isApi(r)) continue;
  if (r.fromDiskCache) continue; // served from cache: no request reached Artemis
  if (r.status && r.status >= 400) continue; // a file that is not there is not an asset
  if (seen.has(r.url)) continue; // already downloaded earlier in the session
  seen.add(r.url);
  if (SHELL_STEPS.test(r.step)) shell++;
  else {
    perStep.set(r.step, (perStep.get(r.step) ?? 0) + 1);
  }
}

const navigations = [...perStep.entries()].filter(([step]) => NAVIGATION_STEPS.test(step));
const lazyTotal = navigations.reduce((a, [, n]) => a + n, 0);
const other = [...perStep.entries()].filter(([step]) => !NAVIGATION_STEPS.test(step));

console.log(`shell (first paint):            ${shell} files`);
console.log(`navigations:                    ${navigations.length}`);
for (const [step, n] of navigations) console.log(`  ${step.padEnd(46)} ${String(n).padStart(4)} new files`);
if (other.length) {
  console.log('not navigations (already-loaded views):');
  for (const [step, n] of other) console.log(`  ${step.padEnd(46)} ${String(n).padStart(4)} new files`);
}
console.log(`lazily loaded over navigations: ${lazyTotal} files`);
console.log(`distinct files in the session:  ${seen.size}`);
console.log(`=> assets-per-navigation:       ${(lazyTotal / navigations.length).toFixed(1)}`);
