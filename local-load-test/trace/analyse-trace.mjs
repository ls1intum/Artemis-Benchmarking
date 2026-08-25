/** Aggregates trace.jsonl into per-step and per-endpoint tables. */
import fs from 'node:fs';

const file = process.argv[2] ?? 'trace.jsonl';
const rows = fs
  .readFileSync(file, 'utf8')
  .split('\n')
  .filter(Boolean)
  .map(l => JSON.parse(l));

const http = rows.filter(r => r.kind === 'http' || r.kind === 'http-failed');
const ws = rows.filter(r => String(r.kind).startsWith('ws'));

/** Collapse ids so /api/exam/courses/1/exams/1/... groups with its siblings. */
const norm = u => {
  const url = new URL(u);
  let p = url.pathname.replace(/\/\d+(?=\/|$)/g, '/{id}').replace(/\/[0-9a-f]{8,}(?=\/|$)/gi, '/{hash}');
  const keep = ['file', 'commit', 'withSubmission', 'page', 'size'];
  const q = [...url.searchParams.keys()].filter(k => keep.includes(k));
  return p + (q.length ? `?${q.join('&')}` : url.search ? '?…' : '');
};

const isApi = r => /\/(api|management|websocket)\//.test(new URL(r.url).pathname) || new URL(r.url).pathname.startsWith('/api');

console.log('='.repeat(100));
console.log('PER STEP');
console.log('='.repeat(100));
const steps = [...new Set(rows.map(r => r.step))];
for (const s of steps) {
  const rs = http.filter(r => r.step === s);
  if (!rs.length) continue;
  const api = rs.filter(isApi);
  const bytes = rs.reduce((a, r) => a + (r.bytes ?? 0), 0);
  const cached = rs.filter(r => r.fromDiskCache).length;
  console.log(
    `${s.padEnd(42)} total=${String(rs.length).padStart(4)}  api=${String(api.length).padStart(4)}  static=${String(rs.length - api.length).padStart(4)}  cacheHits=${String(cached).padStart(3)}  ${(bytes / 1024).toFixed(0)}KB`,
  );
}

console.log('\n' + '='.repeat(100));
console.log('API / MANAGEMENT ENDPOINTS (method path -> count, steps)');
console.log('='.repeat(100));
const byEp = new Map();
for (const r of http.filter(isApi)) {
  const k = `${r.method} ${norm(r.url)}`;
  const e = byEp.get(k) ?? { n: 0, steps: new Set(), status: new Set(), bytes: 0 };
  e.n++;
  e.steps.add(r.step);
  e.status.add(r.status ?? 'ERR');
  e.bytes += r.bytes ?? 0;
  byEp.set(k, e);
}
[...byEp.entries()]
  .sort((a, b) => b[1].n - a[1].n)
  .forEach(([k, e]) =>
    console.log(
      `${String(e.n).padStart(4)}x  ${k.padEnd(78)} [${[...e.status].join(',')}] ${(e.bytes / 1024).toFixed(1)}KB  steps=${[...e.steps].length}`,
    ),
  );

console.log('\n' + '='.repeat(100));
console.log('STATIC RESOURCES BY TYPE');
console.log('='.repeat(100));
const byType = new Map();
for (const r of http.filter(r => !isApi(r))) {
  const e = byType.get(r.type ?? '?') ?? { n: 0, bytes: 0, cached: 0 };
  e.n++;
  e.bytes += r.bytes ?? 0;
  if (r.fromDiskCache) e.cached++;
  byType.set(r.type ?? '?', e);
}
[...byType.entries()]
  .sort((a, b) => b[1].bytes - a[1].bytes)
  .forEach(([k, e]) =>
    console.log(`${k.padEnd(14)} ${String(e.n).padStart(4)} requests  ${(e.bytes / 1024).toFixed(0)}KB  (${e.cached} from cache)`),
  );

console.log('\n' + '='.repeat(100));
console.log('WEBSOCKET');
console.log('='.repeat(100));
const frames = new Map();
for (const f of ws) {
  const cmd = (f.frame ?? '').split('\n')[0].trim() || f.kind;
  const dest = (f.frame ?? '').match(/(?:destination|subscription):(\S+)/)?.[1] ?? '';
  const k = `${f.kind} ${cmd} ${dest}`;
  frames.set(k, (frames.get(k) ?? 0) + 1);
}
[...frames.entries()].sort((a, b) => b[1] - a[1]).forEach(([k, n]) => console.log(`${String(n).padStart(4)}x  ${k}`));

const totalApi = http.filter(isApi).length;
console.log('\n' + '='.repeat(100));
console.log(`TOTAL http=${http.length}  api=${totalApi}  static=${http.length - totalApi}  wsFrames=${ws.length}`);
console.log(`TOTAL bytes=${(http.reduce((a, r) => a + (r.bytes ?? 0), 0) / 1024 / 1024).toFixed(2)} MB`);
