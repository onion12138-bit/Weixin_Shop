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

# Respect a runtime already selected by nvm/asdf instead of replacing it with Homebrew.
selected_node_bin="${test_root}/selected-node/bin"
mkdir -p "${selected_node_bin}"
printf '#!/bin/sh\nprintf "v24.0.0\\n"\n' >"${selected_node_bin}/node"
chmod +x "${selected_node_bin}/node"
resolved_node="$(
  PATH="${selected_node_bin}:${PATH}" \
  PROJECT_LOCAL_SECRETS_DIR="${test_root}/runtime-check-secrets" \
    bash -c 'source "$1/scripts/project-env.sh" >/dev/null && command -v node' _ "${project_root}"
)"
[[ "${resolved_node}" == "${selected_node_bin}/node" ]] || {
  echo 'FAIL: project-env replaced the explicitly selected Node runtime' >&2
  exit 1
}

generated_secrets="${test_root}/generated-secrets"
generated_key="$({
  env -i \
    HOME="${HOME}" \
    USER="${USER:-}" \
    PATH="/usr/bin:/bin:/usr/sbin:/sbin" \
    PROJECT_LOCAL_SECRETS_DIR="${generated_secrets}" \
    bash -c 'source "$1/scripts/project-env.sh" >/dev/null && printf "%s" "$AI_CONFIG_ENCRYPTION_KEY"' _ "${project_root}"
})"
decoded_key_size="$(
  printf '%s' "${generated_key}" \
    | openssl base64 -d -A \
    | wc -c \
    | tr -d '[:space:]'
)"
[[ "${decoded_key_size}" == "32" ]] || {
  echo "FAIL: generated AI encryption key is not 32 bytes" >&2
  exit 1
}
[[ "$(stat -f '%Lp' "${generated_secrets}/ai-config-encryption-key")" == "600" ]] || {
  echo "FAIL: generated AI encryption key permissions are not 0600" >&2
  exit 1
}
reused_key="$({
  PROJECT_LOCAL_SECRETS_DIR="${generated_secrets}" \
    bash -c 'source "$1/scripts/project-env.sh" >/dev/null && printf "%s" "$AI_CONFIG_ENCRYPTION_KEY"' _ "${project_root}"
})"
[[ "${generated_key}" == "${reused_key}" ]] || {
  echo "FAIL: local AI encryption key was rotated" >&2
  exit 1
}

concurrent_secrets="${test_root}/concurrent-secrets"
concurrent_outputs="${test_root}/concurrent-outputs"
mkdir -p "${concurrent_outputs}"
concurrent_pids=()
for index in 1 2 3 4 5 6 7 8; do
  env -i \
    HOME="${HOME}" \
    USER="${USER:-}" \
    PATH="/usr/bin:/bin:/usr/sbin:/sbin" \
    PROJECT_LOCAL_SECRETS_DIR="${concurrent_secrets}" \
    bash -c 'source "$1/scripts/project-env.sh" >/dev/null && printf "%s\n" "$AI_CONFIG_ENCRYPTION_KEY"' _ "${project_root}" \
    >"${concurrent_outputs}/${index}" &
  concurrent_pids+=("$!")
done
for concurrent_pid in "${concurrent_pids[@]}"; do
  wait "${concurrent_pid}"
done
unique_concurrent_keys="$(cat "${concurrent_outputs}"/* | sort -u | wc -l | tr -d '[:space:]')"
[[ "${unique_concurrent_keys}" == "1" ]] || {
  echo "FAIL: concurrent launches observed different AI encryption keys" >&2
  exit 1
}
persistent_concurrent_key="$(tr -d '\n' <"${concurrent_secrets}/ai-config-encryption-key")"
first_concurrent_key="$(tr -d '\n' <"${concurrent_outputs}/1")"
[[ "${persistent_concurrent_key}" == "${first_concurrent_key}" ]] || {
  echo "FAIL: concurrent launch key differs from persisted key" >&2
  exit 1
}

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
    if [[ "${START_LOCAL_TEST_OLD_8081:-0}" == "1" ]]; then
      printf '%s\n' '{"status":"UP"}'
      exit 0
    fi
    if [[ "${START_LOCAL_TEST_FOREIGN_8081:-0}" == "1" ]]; then
      printf '%s\n' '{"status":"UP"}'
      exit 0
    fi
    grep -q 'BACKEND_PORT=8081 mvn spring-boot:run' "${START_LOCAL_TEST_TRACE}" || exit 22
    printf '%s\n' '{"status":"UP"}'
    ;;
  http://localhost:8081/api/public/project-health)
    if [[ "${START_LOCAL_TEST_OLD_8081:-0}" == "1" ]]; then
      if kill -0 "${START_LOCAL_TEST_OLD_PID}" 2>/dev/null; then
        printf '%s\n' '{"name":"DIY 手串定制商城","status":"UP"}'
      else
        printf '%s\n' '{"name":"DIY 手串定制商城","status":"UP","aiEncryptionReady":true}'
      fi
      exit 0
    fi
    if [[ "${START_LOCAL_TEST_FOREIGN_8081:-0}" == "1" ]]; then
      printf '%s\n' '{"name":"其他 Spring 服务","status":"UP"}'
      exit 0
    fi
    grep -q 'BACKEND_PORT=8081 mvn spring-boot:run' "${START_LOCAL_TEST_TRACE}" || exit 22
    printf '%s\n' '{"name":"DIY 手串定制商城","status":"UP","aiEncryptionReady":true}'
    ;;
  http://localhost:8082/actuator/health)
    grep -q 'BACKEND_PORT=8082 mvn spring-boot:run' "${START_LOCAL_TEST_TRACE}" || exit 22
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
if [[ "${START_LOCAL_TEST_OLD_8081:-0}" == "1" ]]; then
  if [[ " $* " == *" -d cwd "* ]]; then
    printf 'p%s\nn%s/backend\n' \
      "${START_LOCAL_TEST_OLD_PID}" \
      "${START_LOCAL_TEST_PROJECT_ROOT}"
    exit 0
  fi
  if [[ " $* " == *" -iTCP:8081 "* ]] \
    && kill -0 "${START_LOCAL_TEST_OLD_PID}" 2>/dev/null; then
    [[ " $* " == *" -t "* ]] && printf '%s\n' "${START_LOCAL_TEST_OLD_PID}"
    exit 0
  fi
fi
case " $* " in
  *" -iTCP:8081 "*) [[ "${START_LOCAL_TEST_OCCUPY_8081:-0}" == "1" ]] && exit 0 ;;
  *) exit 1 ;;
esac
EOF

cat >"${fake_bin}/open" <<'EOF'
#!/usr/bin/env bash
echo "open $*" >>"${START_LOCAL_TEST_TRACE}"
EOF

chmod +x "${fake_bin}"/*

occupied_output="${test_root}/occupied-output.log"
if PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
  START_LOCAL_SKIP_ENV=1 \
  START_LOCAL_NO_OPEN=1 \
  START_LOCAL_WAIT_INTERVAL=0.05 \
  START_LOCAL_WAIT_ATTEMPTS=20 \
  START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
  START_LOCAL_TEST_TRACE="${trace_file}" \
  START_LOCAL_TEST_OCCUPY_8081=1 \
  BACKEND_PORT=8081 \
  bash "${startup_script}" >"${occupied_output}" 2>&1; then
  echo "FAIL: launcher scanned past occupied backend port 8081" >&2
  cat "${occupied_output}" >&2
  cat "${trace_file}" >&2
  exit 1
fi

if grep -Fq 'BACKEND_PORT=8082 mvn spring-boot:run' "${trace_file}"; then
  echo "FAIL: launcher started the backend on fallback port 8082" >&2
  cat "${trace_file}" >&2
  exit 1
fi

: >"${trace_file}"

foreign_output="${test_root}/foreign-output.log"
if PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
  START_LOCAL_SKIP_ENV=1 \
  START_LOCAL_NO_OPEN=1 \
  START_LOCAL_WAIT_INTERVAL=0.05 \
  START_LOCAL_WAIT_ATTEMPTS=20 \
  START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
  START_LOCAL_TEST_TRACE="${trace_file}" \
  START_LOCAL_TEST_OCCUPY_8081=1 \
  START_LOCAL_TEST_FOREIGN_8081=1 \
  BACKEND_PORT=8081 \
  bash "${startup_script}" >"${foreign_output}" 2>&1; then
  echo "FAIL: launcher reused a foreign healthy Spring service on port 8081" >&2
  cat "${foreign_output}" >&2
  cat "${trace_file}" >&2
  exit 1
fi

: >"${trace_file}"

(
  cd "${project_root}/backend"
  sleep 60
) &
old_backend_pid=$!
old_backend_output="${test_root}/old-backend-output.log"
if ! PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
  START_LOCAL_SKIP_ENV=1 \
  START_LOCAL_NO_OPEN=1 \
  START_LOCAL_WAIT_INTERVAL=0.05 \
  START_LOCAL_WAIT_ATTEMPTS=20 \
  START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
  START_LOCAL_TEST_TRACE="${trace_file}" \
  START_LOCAL_TEST_OLD_8081=1 \
  START_LOCAL_TEST_OLD_PID="${old_backend_pid}" \
  START_LOCAL_TEST_PROJECT_ROOT="${project_root}" \
  BACKEND_PORT=8081 \
  bash "${startup_script}" >"${old_backend_output}" 2>&1; then
  kill "${old_backend_pid}" 2>/dev/null || true
  echo "FAIL: launcher did not safely restart the current project's old backend" >&2
  cat "${old_backend_output}" >&2
  cat "${trace_file}" >&2
  test ! -f "${runtime_dir}/backend.log" || cat "${runtime_dir}/backend.log" >&2
  exit 1
fi
if kill -0 "${old_backend_pid}" 2>/dev/null; then
  kill "${old_backend_pid}" 2>/dev/null || true
  echo "FAIL: old project backend was not stopped" >&2
  exit 1
fi
wait "${old_backend_pid}" 2>/dev/null || true
grep -Fq 'BACKEND_PORT=8081 mvn spring-boot:run' "${trace_file}" || {
  echo "FAIL: old project backend was stopped but not restarted" >&2
  cat "${trace_file}" >&2
  exit 1
}

: >"${trace_file}"

if ! PATH="${fake_bin}:/usr/bin:/bin:/usr/sbin:/sbin" \
  START_LOCAL_SKIP_ENV=1 \
  START_LOCAL_NO_OPEN=1 \
  START_LOCAL_WAIT_INTERVAL=0.05 \
  START_LOCAL_WAIT_ATTEMPTS=20 \
  START_LOCAL_RUNTIME_DIR="${runtime_dir}" \
  START_LOCAL_TEST_TRACE="${trace_file}" \
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
    PROJECT_LOCAL_SECRETS_DIR="${test_root}/finder-secrets" \
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

runtime_scan_status=0
grep -RInF "localhost:8080" \
  "${project_root}/.env.example" \
  "${project_root}/backend/src/main/resources" \
  "${project_root}/admin-web/src" \
  "${project_root}/miniprogram/miniprogram" \
  "${project_root}/scripts/start-local.sh" \
  "${project_root}/scripts/catalog-smoke.sh" \
  "${project_root}/scripts/diy-smoke.sh" \
  "${project_root}/scripts/order-smoke.sh" || runtime_scan_status=$?
if [[ "${runtime_scan_status}" -eq 0 ]]; then
  echo "FAIL: active runtime files still route clients to localhost:8080" >&2
  exit 1
elif [[ "${runtime_scan_status}" -ne 1 ]]; then
  echo "FAIL: could not scan active runtime files for stale backend URLs" >&2
  exit 1
fi

echo "start-local tests passed"
