# Browser traces

What a **real browser** asks Artemis for, recorded so it can be compared with what the simulation asks for.

The benchmark's job is to reproduce the load a cohort of students causes. That claim is only worth anything if someone
checks it against a real client now and then — Artemis changes its endpoints, splits its bundle differently, and adds
views, and every one of those changes quietly moves the target. These scripts are how the check is made.

## What is here

| File                | What it does                                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------------------- |
| `trace-exam.mjs`    | drives one student through a whole exam — login, course, exam, every exercise, hand-in, summary               |
| `trace-course.mjs`  | drives one student through the course views instead — dashboard, course, exercises, one exercise, its channel |
| `analyse-trace.mjs` | aggregates a recording per journey step, endpoint, resource type and websocket topic                          |
| `calibrate.mjs`     | derives the per-navigation asset budget from a recording                                                      |

Each trace runs in a **fresh browser context** — an incognito window: empty cookie jar, empty cache — so everything the
client needs is fetched from the server, and records every request through the Chrome DevTools Protocol.

## Running one

Needs a local Artemis with a prepared exam. `run-load-test.sh` in the parent directory builds exactly that; start it
with `KEEP_RUNNING=1` and `ARTEMIS_LOCAL_CLEANUP_ENABLED=false` so the course survives the run, then take a student who
has **not** started the exam yet:

```bash
cd local-load-test/trace
ln -sfn ../../node_modules node_modules          # the scripts import playwright from the repo

ARTEMIS_URL=http://localhost:18080 \
  STUDENT_USER=load_student_7 STUDENT_PASS=Artemis.Test.7 STUDENT_FULLNAME="Load7 Student7" \
  COURSE_ID=13 EXAM_ID=13 DWELL_MS=120000 \
  node trace-exam.mjs

node analyse-trace.mjs trace.jsonl
node calibrate.mjs trace.jsonl
```

`trace-course.mjs` takes `COURSE_ID` and `EXERCISE_ID` instead of `EXAM_ID`, and does not need an unstarted exam.

## Reading the result the right way

**Use the server's access log, not the browser's numbers.** Chrome reports files as cache hits that the load balancer's
log shows it served; a count taken from the browser side came out about 20 % low. The authoritative figures come from

```bash
docker logs artemis-loadtest-nginx-lb-1 2>&1 | grep HeadlessChrome
```

**A `page.goto` is not a click.** Navigating with `goto` re-bootstraps the client, which opens a fresh websocket every
time. An early reading of these traces concluded that a student holds four websocket connections; a student clicking
through the app holds one. If a count looks surprising, check whether the recorder caused it before changing the
simulation to match.

## What the traces established

As of Artemis 10.0, one student sitting one exam:

|                       | Value                                                 |
| --------------------- | ----------------------------------------------------- |
| Requests              | 631–632                                               |
| … static files        | 546 requests over 538 distinct files                  |
| Bytes                 | 20.4 MB                                               |
| Websocket connections | 1 (see the `goto` caveat above)                       |
| Websocket topics      | 10                                                    |
| `/api/public/time`    | 31, in bursts on view initialisation, none while idle |
| Submission auto-save  | every 30 s while the submission is dirty              |

Two independent runs of `trace-exam.mjs`, with different students on different exams, agreed to within one request.
Those numbers are what `BrowserSimulationSettings` is calibrated against; re-run these scripts after an Artemis upgrade
and compare before trusting a benchmark result.
