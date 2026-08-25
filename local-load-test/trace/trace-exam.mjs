/**
 * Records EVERY server interaction a real browser makes while a student walks through an
 * Artemis exam, using a fresh (incognito) browser context.
 *
 * Output: one JSON line per HTTP request / websocket frame, tagged with the journey step.
 */
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = process.env.ARTEMIS_URL ?? 'http://localhost:18080';
const USER = process.env.STUDENT_USER ?? 'load_student_4';
const PASS = process.env.STUDENT_PASS ?? 'Artemis.Test.4';
const COURSE_ID = process.env.COURSE_ID;
const EXAM_ID = process.env.EXAM_ID;
const OUT = process.env.OUT ?? 'trace.jsonl';
const SHOTS = process.env.SHOTS ?? 'shots';
const DWELL_MS = Number(process.env.DWELL_MS ?? 90_000);
const FULLNAME = process.env.STUDENT_FULLNAME ?? 'Load4 Student4';

if (!COURSE_ID || !EXAM_ID) throw new Error('COURSE_ID and EXAM_ID are required');
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
// A fresh context is exactly what an incognito window is: empty cookie jar, empty cache,
// empty storage. Everything the page needs is therefore fetched from the server.
const ctx = await browser.newContext({ viewport: { width: 1512, height: 950 } });
const page = await ctx.newPage();

// --- CDP gives the accurate picture: resource type, cache hits, encoded bytes, ws frames ----
const cdp = await ctx.newCDPSession(page);
await cdp.send('Network.enable');
const inflight = new Map();
cdp.on('Network.requestWillBeSent', e => {
  inflight.set(e.requestId, { url: e.request.url, method: e.request.method, type: e.type, step });
});
cdp.on('Network.responseReceived', e => {
  const r = inflight.get(e.requestId) ?? {};
  Object.assign(r, {
    status: e.response.status,
    mime: e.response.mimeType,
    fromDiskCache: !!e.response.fromDiskCache,
    fromServiceWorker: !!e.response.fromServiceWorker,
    type: e.type ?? r.type,
    protocol: e.response.protocol,
  });
  inflight.set(e.requestId, r);
});
cdp.on('Network.loadingFinished', e => {
  const r = inflight.get(e.requestId);
  if (!r) return;
  rec({ kind: 'http', ...r, bytes: Math.round(e.encodedDataLength) });
  inflight.delete(e.requestId);
});
cdp.on('Network.loadingFailed', e => {
  const r = inflight.get(e.requestId);
  if (!r) return;
  rec({ kind: 'http-failed', ...r, error: e.errorText, canceled: !!e.canceled });
  inflight.delete(e.requestId);
});
cdp.on('Network.webSocketCreated', e => rec({ kind: 'ws-open', url: e.url }));
cdp.on('Network.webSocketClosed', () => rec({ kind: 'ws-close' }));
const wsFrame = dir => e => {
  const payload = String(e.response?.payloadData ?? '');
  // STOMP frames: keep the command + destination, drop the (large, uninteresting) body.
  const head = payload.split('\n\n')[0].slice(0, 300);
  rec({ kind: `ws-${dir}`, frame: head });
};
cdp.on('Network.webSocketFrameSent', wsFrame('sent'));
cdp.on('Network.webSocketFrameReceived', wsFrame('recv'));

const shot = async name => page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: false }).catch(() => {});
const settle = (ms = 2500) => page.waitForTimeout(ms);

try {
  // ---------------------------------------------------------------- 1. cold app load
  setStep('01-cold-load-login-page');
  await page.goto(`${BASE}/`, { waitUntil: 'load' });
  await settle(4000);
  await shot('01-login');

  // ---------------------------------------------------------------- 1b. open the login page
  setStep('01b-open-sign-in-page');
  const loginBtn = page.getByText(/^log in$/i).first();
  if (await loginBtn.isVisible().catch(() => false)) {
    await loginBtn.click();
  } else {
    await page.goto(`${BASE}/sign-in`, { waitUntil: 'load' });
  }
  await page.locator('#username').waitFor({ state: 'visible', timeout: 30_000 });
  await settle(3000);
  await shot('01b-sign-in');

  // ------------------------------------------------- 2a. identifier-first step ("Continue")
  setStep('02a-login-identifier-step');
  await page.locator('#username').fill(USER);
  await page
    .getByRole('button', { name: /continue/i })
    .first()
    .click();
  await page.locator('#password').waitFor({ state: 'visible', timeout: 30_000 });
  await settle(3000);
  await shot('02a-password-step');

  // ------------------------------------------------- 2b. password step
  setStep('02b-login-password-step');
  await page.locator('#password').fill(PASS);
  await page
    .getByRole('button', { name: /^sign in$/i })
    .first()
    .click();
  await page.waitForURL('**/courses**', { timeout: 60_000 }).catch(() => {});
  await settle(6000);
  await shot('02b-after-login');

  // ---------------------------------------------------------------- 3. course dashboard
  setStep('03-course-dashboard');
  await page.goto(`${BASE}/courses`, { waitUntil: 'load' });
  await settle(4000);
  await shot('03-courses');

  // ---------------------------------------------------------------- 4. course -> exams tab
  setStep('04-course-exams-tab');
  await page.goto(`${BASE}/courses/${COURSE_ID}/exams`, { waitUntil: 'load' });
  await settle(4000);
  await shot('04-exams');

  // ---------------------------------------------------------------- 5. exam welcome screen
  setStep('05-exam-welcome-screen');
  await page.goto(`${BASE}/courses/${COURSE_ID}/exams/${EXAM_ID}`, { waitUntil: 'load' });
  await settle(5000);
  await shot('05-exam-welcome');

  // ---------------------------------------------------------------- 6. start the exam
  setStep('06-start-exam');
  const fullname = page.locator('#fullname');
  if (await fullname.isVisible().catch(() => false)) {
    await fullname.fill(FULLNAME);
  }
  const confirm = page.locator('#confirmBox');
  if (await confirm.isVisible().catch(() => false)) {
    await confirm.check();
  }
  const startBtn = page.locator('#start-exam');
  if (await startBtn.isVisible().catch(() => false)) {
    await startBtn.click();
  }
  await settle(8000);
  await shot('06-conduction');

  // ---------------------------------------------------------------- 7. walk the exercises
  // The exam navigation sidebar renders one `a.w-100` per exercise group.
  const navLinks = page.locator('a.w-100');
  const count = await navLinks.count().catch(() => 0);
  console.log(`exam navigation entries: ${count}`);
  rec({ kind: 'note', note: `navigation entries: ${count}` });
  const labels = [];
  for (let i = 0; i < count; i++) {
    labels.push(
      (
        await navLinks
          .nth(i)
          .innerText()
          .catch(() => `#${i}`)
      )
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, 40),
    );
  }
  for (let i = 0; i < count; i++) {
    const slug = labels[i].replace(/[^a-zA-Z0-9]+/g, '_');
    setStep(`07-open-exercise-${i}-${slug}`);
    await navLinks
      .nth(i)
      .click()
      .catch(e => rec({ kind: 'error', error: String(e) }));
    await settle(7000);
    await shot(`07-ex-${i}-${slug}`);

    // Work on the exercise the way a student would, so the client's save/submit traffic shows up.
    setStep(`08-work-on-exercise-${i}-${slug}`);
    const editor = page.locator('textarea:visible, .monaco-editor textarea, .ace_text-input').first();
    if (await editor.isVisible().catch(() => false)) {
      await editor.click({ timeout: 5000 }).catch(() => {});
      await page.keyboard.type('Lorem ipsum dolor sit amet, consetetur sadipscing elitr. ', { delay: 25 });
    }
    const quizOption = page.locator('.answer-option, .quiz-answer-option, input[type=checkbox]:visible').first();
    if (await quizOption.isVisible().catch(() => false)) {
      await quizOption.click({ timeout: 5000 }).catch(() => {});
    }
    await settle(8000);
    await shot(`08-ex-${i}-${slug}`);
  }

  // ---------------------------------------------------------------- 9. idle inside the exam
  // The conduction view keeps syncing/polling while a student thinks. This is the traffic the
  // benchmark's "one request per exercise" model does not reproduce.
  setStep('09-idle-in-exam');
  await settle(DWELL_MS);
  await shot('09-idle');

  // ---------------------------------------------------------------- 10. hand in early
  setStep('10-hand-in-early');
  await page
    .locator('#hand-in-early')
    .click({ timeout: 30_000 })
    .catch(e => rec({ kind: 'error', error: String(e) }));
  await settle(4000);
  const confirmEnd = page.locator('#confirmBox');
  if (await confirmEnd.isVisible().catch(() => false)) await confirmEnd.check().catch(() => {});
  const nameEnd = page.locator('#fullname');
  if (await nameEnd.isVisible().catch(() => false)) await nameEnd.fill(FULLNAME).catch(() => {});
  await page
    .locator('#end-exam')
    .click({ timeout: 30_000 })
    .catch(e => rec({ kind: 'error', error: String(e) }));
  await settle(10_000);
  await shot('10-handed-in');

  // ---------------------------------------------------------------- 11. summary
  setStep('11-exam-summary');
  await page
    .locator('#showExamSummaryButton')
    .click({ timeout: 30_000 })
    .catch(e => rec({ kind: 'error', error: String(e) }));
  await settle(12_000);
  await shot('11-summary');
} catch (err) {
  rec({ kind: 'error', error: String(err) });
  console.error('JOURNEY ERROR', err);
  await shot('99-error');
  fs.writeFileSync(`${SHOTS}/99-error.html`, await page.content().catch(() => ''));
} finally {
  setStep('done');
  await settle(1500);
  out.end();
  await browser.close();
  console.log(`\nwrote ${OUT}`);
}
