# Local multi-node load test

A one-command, fully automatic performance test: it stands up a **multi-node Artemis cluster**,
runs the benchmark against it with _N_ users, writes a timestamped report, and tears everything
down again. Intended to be run regularly to spot performance regressions.

```bash
cd local-load-test
./run-load-test.sh                 # 500 users (default)
NUM_USERS=50 ./run-load-test.sh    # smaller / faster run
KEEP_RUNNING=1 ./run-load-test.sh  # leave the stack up afterwards for inspection
```

## What it spins up

A plain-HTTP mirror of the official `test-server-multi-node-postgresql-localci` topology
(`multi-node-artemis.yml`):

| Component | Role |
|---|---|
| `artemis-app-node-1` | core + scheduling + LocalVC + LocalCI (serves REST/web + git) |
| `artemis-app-node-2` | core + build agent + LocalVC + LocalCI (serves REST/web + git) |
| `artemis-app-node-3` | dedicated build agent |
| `nginx-lb` | plain-HTTP load balancer over node-1 + node-2 → host `:18080` |
| `artemis-postgres` | shared database |
| `artemis-activemq-broker` | STOMP relay distributing **websocket** messages across nodes |
| `artemis-jhipster-registry` | Eureka / Hazelcast clustering |

The benchmark runs from source on the host and drives the cluster through the load balancer, so
REST, git clone/push and the exam websockets are all spread across the two core nodes.

It deliberately runs over **plain HTTP** (not the cluster's self-signed HTTPS) so the host-side
benchmark can do REST + JGit + websockets without certificate plumbing. The cluster wiring
(profiles, Hazelcast, Eureka, broker addresses) is reused verbatim from your Artemis checkout's
own env files; only the host-reachable public URLs are overridden.

## Requirements

- **Docker / Docker Desktop with ≥ ~16 GB allocated** (three Artemis nodes + broker + registry +
  Postgres). The script prints a warning if it detects less.
- A local **Artemis checkout** (for the cluster env files). Defaults to `/Users/krusche/Projects/Artemis`;
  override with `ARTEMIS_DIR=/path/to/Artemis`.
- The benchmark's own build prerequisites (JDK + `gradlew`) — the benchmark app runs via `bootRun`.

## Output

A report is written to `local-load-test/results/loadtest-<N>users-<timestamp>.txt` with, per request
type, the count, average response time, worst single-second average, and peak requests/second —
plus overall wall clock, peak throughput and failed-request count. Raw benchmark logs are kept
alongside as `benchmark-<timestamp>.log`. (`results/` is git-ignored.)

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `NUM_USERS` | `500` | number of simulated students |
| `ONLINE_IDE_PCT` / `PASSWORD_PCT` / `TOKEN_PCT` / `SSH_PCT` | `0/100/0/0` | git auth mix (must sum to 100) |
| `COMMITS_FROM` / `COMMITS_TO` | `1` / `2` | random commits/pushes per student, `[from, to)` |
| `ARTEMIS_DIR` | `/Users/krusche/Projects/Artemis` | local Artemis checkout |
| `ARTEMIS_NODE_XMX` | `2560m` | heap per Artemis node |
| `ARTEMIS_BUILD_ARCH` | `arm64` | `arm64` (Apple Silicon) or `amd64` (Intel) |
| `KEEP_RUNNING` | `0` | `1` keeps the stack up after the run |

Default is 100 % offline/password git (the heaviest path — git clone/push is the known bottleneck),
which matches the existing 500-user baseline so results are comparable across runs. Switch to e.g.
`ONLINE_IDE_PCT=50 PASSWORD_PCT=50` to also exercise the online-IDE repository endpoints.

## Caveats

This runs an entire production-shaped Artemis cluster **and** the benchmark on one machine, so the
absolute numbers reflect local resource contention — the **relative ranking and run-over-run trend**
are the signal, not the absolute milliseconds. For representative absolute figures, point the
benchmark at a dedicated test server instead.

It never touches any other local Artemis stack: every container is project-scoped (`artemis-loadtest`)
and uses network aliases instead of fixed global container names, and only the named volumes
`artemis-loadtest-*` are created/removed.
