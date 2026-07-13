#!/usr/bin/env bash
set -uo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "启动失败：$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少依赖：$1"
}

if [[ "${START_LOCAL_SKIP_ENV:-0}" != "1" ]]; then
  # shellcheck source=project-env.sh
  source "${project_root}/scripts/project-env.sh" || fail "项目环境加载失败"
fi

runtime_dir="${START_LOCAL_RUNTIME_DIR:-${project_root}/.local-run}"
backend_log="${runtime_dir}/backend.log"
admin_log="${runtime_dir}/admin-web.log"
backend_port_file="${runtime_dir}/backend.port"
backend_pid_file="${runtime_dir}/backend.pid"
admin_pid_file="${runtime_dir}/admin-web.pid"
wait_interval="${START_LOCAL_WAIT_INTERVAL:-2}"
wait_attempts="${START_LOCAL_WAIT_ATTEMPTS:-60}"
admin_url="http://localhost:5173"

require_command docker
require_command mvn
require_command node
require_command curl
require_command lsof
require_command nohup

mkdir -p "${runtime_dir}" || fail "无法创建运行目录 ${runtime_dir}"

backend_ready() {
  local port="$1"
  local actuator_response project_response

  actuator_response="$(
    curl -fsS --max-time 2 "http://localhost:${port}/actuator/health" 2>/dev/null
  )" || return 1
  grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' \
    <<<"${actuator_response}" || return 1

  project_response="$(
    curl -fsS --max-time 2 \
      "http://localhost:${port}/api/public/project-health" 2>/dev/null
  )" || return 1
  grep -Eq '"name"[[:space:]]*:[[:space:]]*"DIY 手串定制商城"' \
    <<<"${project_response}" \
    && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' \
      <<<"${project_response}"
}

admin_ready() {
  curl -fsS --max-time 2 "${admin_url}/" 2>/dev/null \
    | grep -Fq 'DIY 手串定制商城管理后台'
}

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

choose_backend_port() {
  local configured_port="$1"

  if backend_ready "${configured_port}"; then
    echo "${configured_port}"
    return 0
  fi

  if ! port_in_use "${configured_port}"; then
    echo "${configured_port}"
    return 0
  fi

  return 1
}

wait_until() {
  local description="$1"
  local check_function="$2"
  local check_argument="${3:-}"
  local attempt=1

  while [[ "${attempt}" -le "${wait_attempts}" ]]; do
    if [[ -n "${check_argument}" ]]; then
      "${check_function}" "${check_argument}" && return 0
    else
      "${check_function}" && return 0
    fi
    sleep "${wait_interval}"
    attempt=$((attempt + 1))
  done

  fail "等待${description}超时"
}

start_backend() {
  local port="$1"
  printf '\n[%s] 启动后端，端口 %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "${port}" >>"${backend_log}"
  (
    cd "${project_root}/backend" || exit 1
    BACKEND_PORT="${port}" nohup mvn spring-boot:run >>"${backend_log}" 2>&1 &
    echo "$!" >"${backend_pid_file}"
  ) || fail "后端进程启动失败，日志：${backend_log}"
}

start_admin() {
  local backend_port="$1"
  printf '\n[%s] 启动管理后台\n' "$(date '+%Y-%m-%d %H:%M:%S')" >>"${admin_log}"
  (
    cd "${project_root}/admin-web" || exit 1
    VITE_API_BASE_URL="http://localhost:${backend_port}/api" \
      nohup node node_modules/vite/bin/vite.js \
        --host localhost --port 5173 --strictPort >>"${admin_log}" 2>&1 &
    echo "$!" >"${admin_pid_file}"
  ) || fail "管理后台进程启动失败，日志：${admin_log}"
}

echo "[1/4] 启动 MySQL 和 Redis..."
(
  cd "${project_root}" || exit 1
  docker compose up -d --wait --wait-timeout 90
) || fail "Docker 服务启动失败，请确认 Docker Desktop 正在运行"

configured_backend_port="${BACKEND_PORT:-8081}"
backend_port="$(choose_backend_port "${configured_backend_port}")" \
  || fail "端口 ${configured_backend_port} 已被其他程序占用，且不是健康的项目后端；不会结束占用进程"
echo "${backend_port}" >"${backend_port_file}"

echo "[2/4] 检查 Spring Boot 后端..."
if backend_ready "${backend_port}"; then
  echo "复用已运行的项目后端（端口 ${backend_port}）"
else
  start_backend "${backend_port}"
  if ! wait_until "后端服务；请查看 ${backend_log}" backend_ready "${backend_port}"; then
    exit 1
  fi
fi

echo "[3/4] 检查 Vue 管理后台..."
if admin_ready; then
  echo "复用已运行的管理后台"
elif port_in_use 5173; then
  fail "5173 端口已被其他程序占用，请先关闭该程序"
else
  start_admin "${backend_port}"
  if ! wait_until "管理后台；请查看 ${admin_log}" admin_ready; then
    exit 1
  fi
fi

echo "[4/4] 服务已全部就绪"
if [[ "${START_LOCAL_NO_OPEN:-0}" != "1" ]]; then
  open "${admin_url}" || fail "浏览器打开失败，请手动访问 ${admin_url}"
fi

echo
echo "管理后台已就绪：${admin_url}"
echo "默认账号：${ADMIN_USERNAME:-admin}"
echo "默认密码：${ADMIN_PASSWORD:-Admin@123}"
echo "后端端口：${backend_port}"
echo "后端日志：${backend_log}"
echo "后台日志：${admin_log}"
