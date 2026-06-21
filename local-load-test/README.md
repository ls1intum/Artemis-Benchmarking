# Local multi-node load test

Run a realistic Artemis exam **load test on your own machine with a single command**. The script
starts a production-shaped **multi-node Artemis cluster**, points the benchmark at it, simulates an
exam with _N_ students, writes a performance report, and cleans everything up again.

It is designed to be run **regularly** (e.g. before/after a change, or on a schedule) so you can spot
performance regressions over time.

> **New to this repo? Start here.** You do not need to understand the benchmark internals. Follow
> [Prerequisites](#prerequisites) once, then run the one command in [Quick start](#quick-start).

---

## Table of contents

- [What it does](#what-it-does)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [The work mix explained](#the-work-mix-explained)
- [Reading the report](#reading-the-report)
- [Does this work on an empty database / fresh clone?](#does-this-work-on-an-empty-database--fresh-clone)
- [How it works under the hood](#how-it-works-under-the-hood)
- [Troubleshooting](#troubleshooting)
- [Caveats](#caveats)

---

## What it does

In one run the script:

1. Boots a **multi-node Artemis cluster** in Docker (`multi-node-artemis.yml`):

   | Component | Role |
   |---|---|
   | `artemis-app-node-1` | core + scheduling + LocalVC + LocalCI (serves REST/web + git) |
   | `artemis-app-node-2` | core + build agent + LocalVC + LocalCI (serves REST/web + git) |
   | `artemis-app-node-3` | dedicated build agent |
   | `nginx-lb` | HTTP load balancer over node-1 + node-2 → **http://localhost:18080** |
   | `artemis-postgres` | shared database |
   | `artemis-activemq-broker` | message broker that distributes **websocket** traffic across nodes |
   | `artemis-jhipster-registry` | service discovery + clustering (Eureka / Hazelcast) |

2. Starts the **benchmark** (from source) with its own throwaway Postgres.
3. Creates an admin and **_N_ student accounts** on Artemis.
4. Runs a full `CREATE_COURSE_AND_EXAM` simulation: each student logs in, navigates into the exam,
   starts it, solves the exercises (quiz, programming via git clone/push or the online editor, …),
   keeps an exam **websocket** open, and submits — exactly like a real exam.
5. Writes a **per-endpoint performance report** to `results/`.
6. **Tears everything down** (unless you ask it not to).

---

## Prerequisites

You need three things. Each is a one-time setup.

### 1. Docker (Docker Desktop) with enough memory

Three Artemis nodes plus the broker, registry and database need roughly **14–16 GB of RAM**.

- Install Docker Desktop and make sure it is running: `docker info` should print without error.
- Give Docker enough memory: **Docker Desktop → Settings → Resources → Memory → at least 16 GB**,
  then *Apply & Restart*. The script prints a warning if it detects less.

Check what Docker currently has:

```bash
docker info --format '{{.MemTotal}}' | awk '{printf "%.1f GB\n", $1/1024/1024/1024}'
```

### 2. A local Artemis checkout

The cluster reuses Artemis' own configuration files (so its wiring always matches the real Artemis).
Clone Artemis next to this repo:

```bash
git clone https://github.com/ls1intum/Artemis.git
```

By default the script looks in `/Users/krusche/Projects/Artemis`. If yours is elsewhere, set
`ARTEMIS_DIR` (see [Configuration](#configuration)). You do **not** need to build or run Artemis
yourself — only the files in `Artemis/docker/` are read.

### 3. A JDK (to run the benchmark)

The benchmark runs from source via the bundled Gradle wrapper, so you need a JDK installed (the same
one used to build this repo — see the repo's top-level README). Check with `java -version`. You do
**not** need to install Gradle separately; `./gradlew` handles it.

---

## Quick start

```bash
cd local-load-test
./run-load-test.sh
```

That's it. The first run is slower because Docker downloads images and Gradle downloads dependencies;
later runs are faster. When it finishes you'll see a report path like
`results/loadtest-500users-<timestamp>.txt`.

Smaller/faster run while you're trying things out:

```bash
NUM_USERS=50 ./run-load-test.sh
```

Keep the cluster running afterwards (e.g. to poke at Artemis in the browser at http://localhost:18080):

```bash
KEEP_RUNNING=1 ./run-load-test.sh
# later, tear it down manually:
docker compose -f multi-node-artemis.yml down -v && docker rm -f artemis-loadtest-bench-pg
```

---

## Configuration

Everything is configurable via environment variables — **no need to edit the script**. Defaults are
in brackets.

### How many users

| Variable | Default | Meaning |
|---|---|---|
| `NUM_USERS` | `500` | number of simulated students |

### The work mix (two independent dimensions)

| Variable | Default | Meaning |
|---|---|---|
| `ONLINE_IDE_PCT` | `50` | % of **all** students who use the **online code editor** (commit via REST). The rest use the **offline IDE** (real `git clone` + `git push`). |
| `OFFLINE_TOKEN_PCT` | `70` | of the **offline** students: % that authenticate git with a **participation token** |
| `OFFLINE_PASSWORD_PCT` | `20` | of the **offline** students: % that authenticate git with **username + password** |
| `OFFLINE_SSH_PCT` | `10` | of the **offline** students: % that authenticate git over **SSH** |

The three `OFFLINE_*_PCT` values must sum to **100** (they describe how the offline half splits).
See [The work mix explained](#the-work-mix-explained) for exactly how these combine.

### How much work per student

| Variable | Default | Meaning |
|---|---|---|
| `COMMITS_FROM` / `COMMITS_TO` | `1` / `2` | random number of commits+pushes per programming student, in the half-open range `[from, to)` |

### Infrastructure / advanced

| Variable | Default | Meaning |
|---|---|---|
| `ARTEMIS_DIR` | `/Users/krusche/Projects/Artemis` | path to your local Artemis checkout |
| `ARTEMIS_NODE_XMX` | `2560m` | Java heap per Artemis node (lower it if Docker memory is tight) |
| `ARTEMIS_BUILD_ARCH` | `arm64` | `arm64` (Apple Silicon) or `amd64` (Intel) |
| `KEEP_RUNNING` | `0` | `1` = leave the whole stack up after the run |

### Examples

```bash
# The default: 500 users, 50% online editor, offline split 70/20/10 token/password/ssh
./run-load-test.sh

# 1000 users, everyone uses offline git over password
NUM_USERS=1000 ONLINE_IDE_PCT=0 OFFLINE_PASSWORD_PCT=100 OFFLINE_TOKEN_PCT=0 OFFLINE_SSH_PCT=0 ./run-load-test.sh

# Everyone on the online editor (no git at all)
ONLINE_IDE_PCT=100 ./run-load-test.sh

# Artemis checked out somewhere else, less heap per node
ARTEMIS_DIR=~/dev/Artemis ARTEMIS_NODE_XMX=2g ./run-load-test.sh
```

---

## The work mix explained

There are **two separate choices** per student:

1. **Which IDE** they use — the **online code editor** (in the browser, commits via REST) or the
   **offline IDE** (a real `git clone` + edit + `git push` from their machine).
2. **How offline students authenticate git** — participation **token**, **password**, or **SSH**.
   (This only applies to offline students; online-editor students don't use git auth.)

Internally the benchmark models this as a single 4-way split (online-IDE / token / password / ssh
that sums to 100). The script computes that for you from your two-dimension settings:

```
online  = ONLINE_IDE_PCT
offline = 100 - ONLINE_IDE_PCT
token    = offline × OFFLINE_TOKEN_PCT    / 100
password = offline × OFFLINE_PASSWORD_PCT / 100
ssh      = offline − token − password           (remainder, so the four always sum to 100)
```

With the defaults (`ONLINE_IDE_PCT=50`, offline split `70/20/10`):

| Mechanism | Share of all students |
|---|---|
| online code editor | **50%** |
| offline git — token | 35% |
| offline git — password | 10% |
| offline git — SSH | 5% |

The report shows both your requested split and this final per-student mix.

### A note on SSH

SSH git uses port **7921**, and the benchmark always connects to `localhost:7921`. The script
exposes the cluster's SSH server on that port **only if it is free**. If port 7921 is already in use
— most commonly because you're running Artemis locally yourself — the script prints a warning and
**folds the SSH share into token auth** so the run still completes the full number of users. To
actually exercise SSH, stop whatever is on 7921 first. (Token and password git, the online editor and
websockets are unaffected.)

---

## Reading the report

`results/loadtest-<N>users-<timestamp>.txt` looks like this (abridged):

```
Users            : 60
IDE split        : online code editor 50% | offline IDE / git 50%
Mechanism mix    : online-IDE 50% | token 40% | password 10% | ssh 0% (of all students)
Wall clock (run) : 24 s
Peak throughput  : 832 requests/s
Failed requests  : 0

        Request type         | Count | Avg ms | Worst-sec avg ms | Peak req/s
 PUSH_PASSWORD               |     6 |   1185 |             1185 |          6
 CLONE_TOKEN                 |    27 |    637 |              637 |         27
 WEBSOCKET                   |    60 |    154 |              154 |         60
 SERVER_TIME                 |   240 |      6 |                8 |        103
 ...
```

- **Count** — how many of that request the simulation made.
- **Avg ms** — average response time.
- **Worst-sec avg ms** — the worst one-second average (spikes show up here even if the overall
  average looks fine).
- **Peak req/s** — busiest single second for that request type.
- **Failed requests** — should be **0**. Anything else points at a broken endpoint; the raw
  benchmark log (`results/benchmark-<timestamp>.log`) has the details.

To compare runs over time, keep the report files and diff them, or watch `CLONE_*` / `PUSH_*`
(git is usually the heaviest), `WEBSOCKET` (connection load) and `SERVER_TIME` (the exam clock poll).

`results/` is git-ignored, so your reports stay local.

---

## Does this work on an empty database / fresh clone?

**Yes — that is the normal case.** Every run starts from a clean slate:

- The cluster is recreated with **fresh, empty volumes** each time (the script runs
  `docker compose down -v` first). On first boot Artemis runs its Liquibase migrations to build the
  schema and seed its internal admin — no pre-existing database is needed.
- The benchmark gets its **own empty Postgres** each run.
- The course, exam, exercises and all student accounts are **created from scratch** by the
  simulation, and every student gets a **freshly created git repository** to clone and push to.

It also works from a **fresh clone of this repository** (and of Artemis): the benchmark builds and
runs from source via `./gradlew`, and only Artemis' committed `docker/` config files are read. The
only cost of "fresh" is time — the first run downloads Docker images and Gradle/Docker layers.

---

## How it works under the hood

- **Plain HTTP, on purpose.** The official multi-node setup serves HTTPS with a self-signed
  certificate. Trusting that for REST **and** JGit **and** websockets all at once is brittle, so this
  harness puts a plain-HTTP load balancer in front of the cluster — the host-side benchmark then
  speaks plain HTTP/ws and plain git, no certificate setup.
- **Reuses Artemis' own config.** The cluster wiring (Spring profiles, Hazelcast, Eureka, broker
  addresses) comes verbatim from `${ARTEMIS_DIR}/docker/artemis/config/*.env`. The harness overrides
  only the public URLs so the host can reach them, and the operator name the prod profile requires.
- **Never disturbs another local Artemis.** All containers are project-scoped (`artemis-loadtest`)
  and use **network aliases** instead of fixed global container names, so this stack can run
  alongside your own Artemis without name clashes. Only the `artemis-loadtest-*` volumes are
  created and removed.

Files in this directory:

| File | Purpose |
|---|---|
| `run-load-test.sh` | the orchestrator (run this) |
| `multi-node-artemis.yml` | the multi-node cluster definition |
| `multi-node-ssh.yml` | optional overlay that publishes SSH git on port 7921 (added automatically when free) |
| `nginx-lb.conf` | the load-balancer config (REST + git + websocket) |
| `results/` | generated reports + benchmark logs (git-ignored) |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `docker daemon not reachable` | Start Docker Desktop. |
| Warning about Docker memory, or nodes crash / never become healthy | Raise Docker Desktop memory to ≥ 16 GB, or lower `ARTEMIS_NODE_XMX`. |
| `ARTEMIS_DIR invalid` | Point `ARTEMIS_DIR` at your Artemis checkout (the folder that contains `docker/artemis/config`). |
| Warning about **port 7921** in use | A local Artemis is using it. Either ignore it (SSH is auto-folded into token) or stop that Artemis to test SSH. |
| `bind: address already in use` for 18080 / 15432 / 8090 | Something already uses the load-balancer / benchmark-DB / benchmark-app port. Stop it, or change `LB_PORT` / `BENCH_DB_PORT` / `BENCH_PORT`. |
| First run is very slow | Expected — Docker images + Gradle deps download once, then it's cached. |
| Want to see what's happening | Tail `results/benchmark-<timestamp>.log` (benchmark) and `docker compose -f multi-node-artemis.yml logs -f artemis-app-node-1` (Artemis). |
| A run got interrupted and left things up | `docker compose -f multi-node-artemis.yml down -v && docker rm -f artemis-loadtest-bench-pg && pkill -f ArtemisBenchmarkingApp` |

---

## Caveats

This runs an entire production-shaped Artemis cluster **and** the benchmark on a single machine, so
the **absolute** numbers reflect local resource contention. Treat the **relative ranking between
endpoints** and the **run-over-run trend** as the signal, not the absolute milliseconds. For
representative absolute figures, point the benchmark at a dedicated test server instead.
