# Management Admin One-Click Local Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a macOS double-click entry that starts Docker infrastructure, the Spring Boot backend, and the Vue admin client, then opens `http://localhost:5173` without terminal input.

**Architecture:** A thin root `.command` file delegates to a testable Bash orchestrator. The orchestrator loads the existing project environment, reuses healthy services, selects a temporary backend port when 8080 belongs to Jenkins or another process, records logs/PIDs under `.local-run/`, and opens the fixed Vue address after both services are ready.

**Tech Stack:** Bash 3.2-compatible shell, macOS `open`/`lsof`, Docker Compose, Maven/Spring Boot, pnpm/Vite, curl.

## Global Constraints

- The user-facing admin URL remains exactly `http://localhost:5173`.
- Do not stop Jenkins or any unknown process occupying port 8080.
- Do not modify `.env`, `miniprogram/project.config.json`, `Pic/`, or WeChat mini-program configuration.
- Local logs and PID files live under `.local-run/` and remain untracked.
- Startup waits are bounded and failures show a useful log path.
- Repeated launches reuse healthy project services instead of creating duplicates.

---

### Task 1: Testable startup orchestrator

**Files:**
- Create: `scripts/start-local.sh`
- Create: `scripts/start-local.test.sh`

**Interfaces:**
- Consumes: `scripts/project-env.sh`, `.env`, `docker compose`, `mvn spring-boot:run`, `pnpm dev`.
- Produces: executable `scripts/start-local.sh`; accepts `START_LOCAL_NO_OPEN=1` for non-GUI verification and command overrides through `PATH` for isolated tests.

- [ ] **Step 1: Write the failing isolated shell test**

Create `scripts/start-local.test.sh` with a temporary fake-command directory. Fake `docker`, `mvn`, `pnpm`, `curl`, `lsof`, and `open` append invocations to a trace file. Run the orchestrator with `START_LOCAL_NO_OPEN=1`, zero-second polling, and the fake directory first in `PATH`. Assert that Docker starts before Maven, Maven receives the selected `BACKEND_PORT`, pnpm receives the matching `VITE_API_BASE_URL`, and browser opening is skipped.

Core assertions:

```bash
assert_contains "docker compose up -d"
assert_order "docker compose up -d" "mvn spring-boot:run"
assert_contains "BACKEND_PORT=8081 mvn spring-boot:run"
assert_contains "VITE_API_BASE_URL=http://localhost:8081/api pnpm dev --host localhost --port 5173 --strictPort"
assert_not_contains "open http://localhost:5173"
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `bash scripts/start-local.test.sh`

Expected: non-zero exit because `scripts/start-local.sh` does not exist.

- [ ] **Step 3: Implement the minimal orchestrator**

Implement focused functions in `scripts/start-local.sh`:

```bash
require_command() { command -v "$1" >/dev/null 2>&1 || fail "缺少依赖：$1"; }
backend_ready() { curl -fsS --max-time 2 "http://localhost:$1/actuator/health" | grep -q '"status":"UP"'; }
port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }
choose_backend_port() { /* reuse configured service or scan 8081..8099 */; }
wait_until() { /* bounded attempts using START_LOCAL_WAIT_INTERVAL */; }
start_backend() { /* nohup with BACKEND_PORT and backend log/PID */; }
start_admin() { /* nohup with VITE_API_BASE_URL and admin log/PID */; }
```

Execution order must be: resolve root → source environment → validate dependencies → create `.local-run/` → Docker Compose → choose/reuse backend → start/wait backend → reuse/start/wait Vue → open URL unless `START_LOCAL_NO_OPEN=1` → print credentials and logs.

- [ ] **Step 4: Run the isolated test and syntax check**

Run:

```bash
bash -n scripts/start-local.sh scripts/start-local.test.sh
bash scripts/start-local.test.sh
```

Expected: syntax check exits 0 and test prints `start-local tests passed`.

- [ ] **Step 5: Commit the orchestrator**

```bash
git add scripts/start-local.sh scripts/start-local.test.sh
git commit -m "feat: add tested local startup orchestrator"
```

### Task 2: Finder double-click entry and ignored runtime state

**Files:**
- Create: `启动管理后台.command`
- Modify: `.gitignore`
- Modify: `docs/setup.md`

**Interfaces:**
- Consumes: executable `scripts/start-local.sh` from Task 1.
- Produces: Finder-double-clickable `启动管理后台.command`; ignored `.local-run/` runtime directory.

- [ ] **Step 1: Write the failing entry-point assertions**

Extend `scripts/start-local.test.sh` to assert:

```bash
test -x "${project_root}/启动管理后台.command"
grep -Fq 'exec "${project_root}/scripts/start-local.sh"' "${project_root}/启动管理后台.command"
grep -Fxq '.local-run/' "${project_root}/.gitignore"
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `bash scripts/start-local.test.sh`

Expected: non-zero exit because the `.command` entry is absent.

- [ ] **Step 3: Add the entry and documentation**

Create this thin entry:

```bash
#!/usr/bin/env bash
set -u
project_root="$(cd "$(dirname "$0")" && pwd)"
exec "${project_root}/scripts/start-local.sh"
```

Add `.local-run/` to `.gitignore`, mark both startup files executable, and add a “双击一键启动” section to `docs/setup.md` containing the URL, credentials, file name, log location, and Jenkins-safe fallback behavior.

- [ ] **Step 4: Run entry tests and file checks**

Run:

```bash
bash -n scripts/start-local.sh scripts/start-local.test.sh "启动管理后台.command"
bash scripts/start-local.test.sh
test -x "启动管理后台.command"
test -x scripts/start-local.sh
git diff --check
```

Expected: all commands exit 0.

- [ ] **Step 5: Commit the user-facing entry**

```bash
git add .gitignore docs/setup.md scripts/start-local.test.sh "启动管理后台.command"
git commit -m "feat: add double-click admin startup"
```

### Task 3: Real local smoke verification

**Files:**
- Verify only: `启动管理后台.command`, `.local-run/backend.log`, `.local-run/admin-web.log`

**Interfaces:**
- Consumes: completed startup entry.
- Produces: evidence that the actual local admin URL and backend API are reachable without touching Jenkins.

- [ ] **Step 1: Capture pre-run protected state**

Run:

```bash
git diff -- miniprogram/project.config.json
git status --short Pic miniprogram/project.config.json
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Expected: the AppID/tool-generated diff and `Pic/` state remain visible; record the current 8080 owner without terminating it.

- [ ] **Step 2: Execute without opening a GUI during automated verification**

Run: `START_LOCAL_NO_OPEN=1 ./启动管理后台.command`

Expected: exit 0 and print `管理后台已就绪：http://localhost:5173`.

- [ ] **Step 3: Verify live endpoints**

Run:

```bash
curl -fsS http://localhost:5173/ >/dev/null
backend_port="$(cat .local-run/backend.port)"
curl -fsS "http://localhost:${backend_port}/actuator/health"
```

Expected: Vue returns HTTP 200 and backend returns JSON containing `"status":"UP"`.

- [ ] **Step 4: Re-run to verify idempotency**

Run: `START_LOCAL_NO_OPEN=1 ./启动管理后台.command`

Expected: exit 0, reports reuse of the healthy services, and keeps the recorded PIDs unchanged.

- [ ] **Step 5: Verify protected worktree state and commit any verification documentation adjustment**

Run:

```bash
git diff -- miniprogram/project.config.json
git status --short
git diff --check
```

Expected: protected user changes are unchanged; `.local-run/` is absent from Git status; only intended implementation files are committed.
