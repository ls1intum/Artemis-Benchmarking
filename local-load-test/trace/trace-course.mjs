/**
 * Records what a real browser asks Artemis for while a student browses a course rather than sits an exam:
 * the dashboard, the course, its exercise list, one exercise and that exercise's channel.
 *
 * The exam trace (`trace-exam.mjs`) never opens any of those views, so the endpoints the benchmark calls for them
 * were unverified. This is the journey that verifies them.
 *
 * Output: one JSON line per HTTP request / websocket frame, tagged with the journey step.
 */
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = process.env.ARTEMIS_URL ?? 'http://localhost:18080';
const USER = process.env.STUDENT_USER ?? 'load_student_1';
const PASS = process.env.STUDENT_PASS ?? 'Artemis.Test.1';
const COURSE_ID = process.env.COURSE_ID;
const EXERCISE_ID = process.env.EXERCISE_ID;
const OUT = process.env.OUT ?? 'trace-course.jsonl';
const SHOTS = process.env.SHOTS ?? 'shots-course';

if (!COURSE_ID) throw new Error('COURSE_ID is required');
fs.mkdirSync(SHOTS, { recursive: true });
const out = fs.createWriteStream(OUT);

let step = 'boot';
const t0 = Date.now();
const rec = o => out.write(JSON.stringify({ t: Date.now() - t0, step, ...o }) + '\n');
const setStep = s => {
  step = s;
  console.log(`\n=== STEP ${s} (+${((Date.now() - t0) / 1000).toFixed(1)}s)`);
  rec({ kind: 'step-marker' });
};

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1512, height: 950 } });
const page = await ctx.newPage();

const cdp = await ctx.newCDPSession(page);
await cdp.send('Network.enable');
const inflight = new Map();
cdp.on('Network.requestWillBeSent', e => {
  inflight.set(e.requestId, { url: e.request.url, method: e.request.method, type: e.type, step });
});
cdp.on('Network.responseReceived', e => {
  const r = inflight.get(e.requestId) ?? {};
  Object.assign(r, { status: e.response.status, fromDiskCache: !!e.response.fromDiskCache, type: e.type ?? r.type });
  inflight.set(e.requestId, r);
});
cdp.on('Network.loadingFinished', e => {
  const r = inflight.get(e.requestId);
  if (!r) return;
  rec({ kind: 'http', ...r, bytes: Math.round(e.encodedDataLength) });
  inflight.delete(e.requestId);
});

const shot = async name => page.screenshot({ path: `${SHOTS}/${name}.png` }).catch(() => {});
const settle = (ms = 3000) => page.waitForTimeout(ms);

try {
  setStep('01-login');
  await page.goto(`${BASE}/sign-in`, { waitUntil: 'load' });
  await page.locator('#username').waitFor({ state: 'visible', timeout: 30_000 });
  await page.locator('#username').fill(USER);
  await page
    .getByRole('button', { name: /continue/i })
    .first()
    .click();
  await page.locator('#password').waitFor({ state: 'visible', timeout: 30_000 });
  await page.locator('#password').fill(PASS);
  await page
    .getByRole('button', { name: /^sign in$/i })
    .first()
    .click();
  await page.waitForURL('**/courses**', { timeout: 60_000 }).catch(() => {});
  await settle(5000);
  await shot('01-after-login');

  setStep('02-course-dashboard');
  await page.goto(`${BASE}/courses`, { waitUntil: 'load' });
  await settle(4000);
  await shot('02-dashboard');

  setStep('03-course-overview');
  await page.goto(`${BASE}/courses/${COURSE_ID}`, { waitUntil: 'load' });
  await settle(6000);
  await shot('03-course');

  setStep('04-exercises-tab');
  await page.goto(`${BASE}/courses/${COURSE_ID}/exercises`, { waitUntil: 'load' });
  await settle(6000);
  await shot('04-exercises');

  setStep('05-open-exercise');
  if (EXERCISE_ID) {
    await page.goto(`${BASE}/courses/${COURSE_ID}/exercises/${EXERCISE_ID}`, { waitUntil: 'load' });
  } else {
    await page
      .locator('a[href*="/exercises/"]')
      .first()
      .click()
      .catch(() => {});
  }
  await settle(8000);
  await shot('05-exercise');

  setStep('06-exercise-channel');
  // The communication sidebar of an exercise; the button label varies, so try the obvious candidates.
  for (const name of [/communication/i, /messages/i, /channel/i, /discussion/i]) {
    const button = page.getByRole('button', { name }).first();
    if (await button.isVisible().catch(() => false)) {
      await button.click().catch(() => {});
      break;
    }
  }
  await settle(6000);
  await shot('06-channel');

  setStep('07-communication-tab');
  await page.goto(`${BASE}/courses/${COURSE_ID}/communication`, { waitUntil: 'load' }).catch(() => {});
  await settle(6000);
  await shot('07-communication');
} catch (err) {
  rec({ kind: 'error', error: String(err) });
  console.error('JOURNEY ERROR', err);
  await shot('99-error');
} finally {
  setStep('done');
  await settle(1500);
  out.end();
  await browser.close();
  console.log(`\nwrote ${OUT}`);
}
