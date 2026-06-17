# End-to-end tests

[Playwright](https://playwright.dev) end-to-end tests that drive the real application
(Angular client → Spring Boot server → PostgreSQL) in a browser. They verify that the
deployed app actually works, not just that it compiles.

- Specs live in `e2e/*.spec.ts`, shared helpers in `e2e/helpers.ts`.
- Configuration is in [`playwright.config.ts`](../playwright.config.ts) (test dir `e2e/`,
  Chromium only, retries on CI). The target URL is `E2E_BASE_URL` (default
  `http://127.0.0.1:8080`); the runner scripts set it for you.

## Prerequisites

- **Node** ≥ 24.15.0 and **pnpm** (`corepack enable` activates the pinned version).
- **Java 25** (to build/run the server).
- **Docker** — required by the docker runner, and by the fast runner unless you already
  have a local PostgreSQL on `:5432`.
- Client dependencies and the Chromium browser:
  ```bash
  pnpm install
  pnpm exec playwright install chromium
  ```
  (The runner scripts install the browser for you.)

## Running the tests

Two helper scripts bring the whole stack up, run the suite, and leave it running so
re-runs are fast. Both support `--stop` to tear everything down, and forward extra
arguments to Playwright (`--ui`, `--headed`, a test filter, …).

### Realistic (Docker) — `./run-e2e-tests-local.sh`

Builds the production WAR, wraps it in a runtime image, and runs the stack (server +
PostgreSQL) via `docker compose`; tests run against `http://127.0.0.1:8080`. Slower, but it
exercises the production artifact.

```bash
./run-e2e-tests-local.sh                 # build + run everything
./run-e2e-tests-local.sh --skip-build    # reuse the existing image
./run-e2e-tests-local.sh --stop          # tear the stack down
```

> The WAR is built on the host (not via `docker build .`): a clean in-container Gradle
> build omits the bundled client from the WAR (in production nginx serves the client),
> which would make the app return 404 for the SPA. A guard fails the script loudly if
> the built WAR ever lacks the client.

### Fast (host) — `./run-e2e-tests-local-fast.sh`

Runs the server (`gradlew bootRun`) and client (`pnpm start`) directly on the host
against PostgreSQL on `:5432` (an existing instance is reused, otherwise a throwaway
container is started); tests run against `http://localhost:9000`. Much faster, and
services stay up between runs.

```bash
./run-e2e-tests-local-fast.sh                                      # start + run
./run-e2e-tests-local-fast.sh --skip-db --skip-server --skip-client # re-run, reuse services
./run-e2e-tests-local-fast.sh --headed --grep "CRUD"               # headed, filtered
./run-e2e-tests-local-fast.sh --stop                               # tear everything down
```

### Against an already-running app

If the app is already up somewhere, run Playwright directly:

```bash
E2E_BASE_URL=http://localhost:9000 pnpm run e2e        # or: pnpm exec playwright test
pnpm exec playwright show-report                       # open the last HTML report
```

### CI

The `e2e` GitHub Actions job (`.github/workflows/ci.yml`) runs after the `test` job
passes, using the same host-process flow as the fast runner, and uploads the Playwright
HTML report as an artifact.

## Useful facts

- The seeded admin login is **`admin` / `admin`** (`user` / `user` also exists); see
  `src/main/resources/config/liquibase/data/user.csv`.
- The Docker stack uses the `prod` profile and a fresh database each run; the fast runner
  uses the `dev` profile against your local `:5432` PostgreSQL.

## Writing new tests

- Add a `e2e/<feature>.spec.ts` file. Use `login(page)` and `collectConsoleErrors(page)`
  from `./helpers`.
- Navigate with **baseURL-relative paths** (`page.goto('/admin/...')`) and prefer
  **role-based locators** (`getByRole`) over CSS where possible.
- For create/edit forms, **wait for the save to complete before navigating away** —
  navigating immediately aborts the in-flight request (see the `save()` helper in
  `user-management-crud.spec.ts`).
- For data that is created/edited/deleted, use a **unique identifier per run**
  (e.g. `` `e2e-user-${Date.now()}` ``), put dependent steps in a
  `test.describe.configure({ mode: 'serial' })` block, and **delete what you create**.
- `e2e/` is excluded from the Angular ESLint config and the Prettier glob; Playwright
  handles its own TypeScript.

## Coverage

**Currently covered**

- _Public_ (`home.spec.ts`): landing page loads with no unexpected console errors; admin
  sign-in.
- _Authenticated_ (`authenticated.spec.ts`): admin navigation after login; the
  simulations page loads cleanly; the metrics page renders live JVM data.
- _Full-vertical CRUD_ (`user-management-crud.spec.ts`, UI → REST → PostgreSQL): create a user,
  list it, persist across reload, view its detail, edit its name, grant the admin
  authority, deactivate, reactivate, cancel a delete, and delete it.

**Not yet covered**

- Running a real **simulation/benchmark** against a live Artemis instance (needs an
  external Artemis server), and therefore the **ngx-charts result charts** that render
  from run data — the simulations view is only checked for clean load/instantiation.
- The **Artemis Users**, **Server Configurations**, **Logs**, **Configuration** and
  **Health** admin screens.
- Real-time **WebSocket/STOMP** updates.
- Cross-browser runs (Chromium only) and mobile viewports.
- Account self-service flows (registration, password reset/change).
