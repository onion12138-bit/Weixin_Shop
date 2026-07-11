#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
startup_script="${project_root}/scripts/start-local.sh"

if [[ ! -f "${startup_script}" ]]; then
  echo "FAIL: missing ${startup_script}" >&2
  exit 1
fi

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

cat >"${fake_bin}/pnpm" <<'EOF'
#!/usr/bin/env bash
echo "VITE_API_BASE_URL=${VITE_API_BASE_URL:-} pnpm $*" >>"${START_LOCAL_TEST_TRACE}"
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
    grep -q 'pnpm dev --host localhost --port 5173 --strictPort' "${START_LOCAL_TEST_TRACE}" || exit 22
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
assert_contains "VITE_API_BASE_URL=http://localhost:8081/api pnpm dev --host localhost --port 5173 --strictPort"
assert_order "docker compose up -d" "BACKEND_PORT=8081 mvn spring-boot:run"
assert_not_contains "open http://localhost:5173"

echo "start-local tests passed"
