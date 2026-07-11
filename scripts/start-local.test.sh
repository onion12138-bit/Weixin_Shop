#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
startup_script="${project_root}/scripts/start-local.sh"
command_entry="${project_root}/启动管理后台.command"

if [[ ! -f "${startup_script}" ]]; then
  echo "FAIL: missing ${startup_script}" >&2
  exit 1
fi

if [[ ! -x "${command_entry}" ]]; then
  echo "FAIL: missing executable ${command_entry}" >&2
  exit 1
fi

grep -Fq '"${project_root}/scripts/start-local.sh"' "${command_entry}" || {
  echo "FAIL: command entry does not delegate to start-local.sh" >&2
  exit 1
}

grep -Fxq '.local-run/' "${project_root}/.gitignore" || {
  echo "FAIL: .local-run/ is not ignored" >&2
  exit 1
}

test_root="$(mktemp -d "${TMPDIR:-/tmp}/weixin-shop-start-local.XXXXXX")"
trap 'rm -rf "${test_root}"' EXIT

fake_bin="${test_root}/bin"
trace_file="${test_root}/trace.log"
runtime_dir="${test_root}/runtime"
mkdir -p "${fake_bin}" "${runtime_dir}"
: >"${trace_file}"

cat >"${fake_bin}/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >>"${START_LOCAL_TEST_TRACE}"
exit 0
EOF

cat >"${fake_bin}/mvn" <<'EOF'
#!/usr/bin/env bash
echo "BACKEND_PORT=${BACKEND_PORT:-} mvn $*" >>"${START_LOCAL_TEST_TRACE}"
exit 0
EOF

cat >"${fake_bin}/node" <<'EOF'
#!/usr/bin/env bash
echo "VITE_API_BASE_URL=${VITE_API_BASE_URL:-} node $*" >>"${START_LOCAL_TEST_TRACE}"
exit 0
EOF

cat >"${fake_bin}/curl" <<'EOF'
#!/usr/bin/env bash
url="${*: -1}"
case "${url}" in
  http://localhost:8080/actuator/health)
    exit 22
    ;;
  http://localhost:8081/actuator/health)
    grep -q 'BACKEND_PORT=8081 mvn spring-boot:run' "${START_LOCAL_TEST_TRACE}" || exit 22
    printf '%s\n' '{"status":"UP"}'
    ;;
  http://localhost:5173/)
    grep -q 'node node_modules/vite/bin/vite.js --host localhost --port 5173 --strictPort' "${START_LOCAL_TEST_TRACE}" || exit 22
    printf '%s\n' '<html><title>DIY 手串定制商城管理后台</title></html>'
    ;;
  *)
    exit 22
    ;;
esac
EOF

cat >"${fake_bin}/lsof" <<'EOF'
#!/usr/bin/env bash
case " $* " in
  *" -iTCP:8080 "*) exit 0 ;;
  *) exit 1 ;;
esac
EOF

cat >"${fake_bin}/open" <<'EOF'
#!/usr/bin/env bash
echo "open $*" >>"${START_LOCAL_TEST_TRACE}"
EOF

chmod +x "${fake_bin}"/*

if ! PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
  START_LOCAL_SKIP_ENV=1 \
  START_LOCAL_NO_OPEN=1 \
  START_LOCAL_WAIT_INTERVAL=0.05 \
  START_LOCAL_WAIT_ATTEMPTS=20 \
  START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
  START_LOCAL_TEST_TRACE="${trace_file}" \
  BACKEND_PORT=8080 \
  ADMIN_USERNAME=admin \
  ADMIN_PASSWORD=Admin@123 \
  bash "${startup_script}" >/dev/null; then
  echo "FAIL: startup script returned non-zero" >&2
  cat "${trace_file}" >&2
  test ! -f "${runtime_dir}/backend.log" || cat "${runtime_dir}/backend.log" >&2
  exit 1
fi

assert_contains() {
  local expected="$1"
  grep -Fq "${expected}" "${trace_file}" || {
    echo "FAIL: trace does not contain: ${expected}" >&2
    cat "${trace_file}" >&2
    exit 1
  }
}

assert_not_contains() {
  local unexpected="$1"
  if grep -Fq "${unexpected}" "${trace_file}"; then
    echo "FAIL: trace unexpectedly contains: ${unexpected}" >&2
    cat "${trace_file}" >&2
    exit 1
  fi
}

assert_order() {
  local first="$1"
  local second="$2"
  local first_line second_line
  first_line="$(grep -Fn "${first}" "${trace_file}" | head -n 1 | cut -d: -f1)"
  second_line="$(grep -Fn "${second}" "${trace_file}" | head -n 1 | cut -d: -f1)"
  if [[ -z "${first_line}" || -z "${second_line}" || "${first_line}" -ge "${second_line}" ]]; then
    echo "FAIL: expected '${first}' before '${second}'" >&2
    cat "${trace_file}" >&2
    exit 1
  fi
}

assert_contains "docker compose up -d"
assert_contains "BACKEND_PORT=8081 mvn spring-boot:run"
assert_contains "VITE_API_BASE_URL=http://localhost:8081/api node node_modules/vite/bin/vite.js --host localhost --port 5173 --strictPort"
assert_order "docker compose up -d" "BACKEND_PORT=8081 mvn spring-boot:run"
assert_not_contains "open http://localhost:5173"

finder_node="$({
  env -i \
    HOME="${HOME}" \
    USER="${USER:-}" \
    TMPDIR="${TMPDIR:-/tmp}" \
    PATH="/usr/bin:/bin:/usr/sbin:/sbin" \
    bash -c 'source "$1/scripts/project-env.sh" >/dev/null && command -v node' _ "${project_root}"
} 2>/dev/null)"
if [[ -z "${finder_node}" ]]; then
  echo "FAIL: project environment does not expose node to Finder launches" >&2
  exit 1
fi

PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
START_LOCAL_SKIP_ENV=1 \
START_LOCAL_NO_OPEN=1 \
START_LOCAL_WAIT_INTERVAL=0.05 \
START_LOCAL_WAIT_ATTEMPTS=20 \
START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
START_LOCAL_TEST_TRACE="${trace_file}" \
BACKEND_PORT=8080 \
ADMIN_USERNAME=admin \
ADMIN_PASSWORD=Admin@123 \
"${command_entry}" >/dev/null 2>&1 &
command_pid=$!
sleep 0.2
if ! kill -0 "${command_pid}" 2>/dev/null; then
  echo "FAIL: double-click entry exited instead of keeping services alive" >&2
  exit 1
fi
kill "${command_pid}"
wait "${command_pid}" 2>/dev/null || true

echo "start-local tests passed"
