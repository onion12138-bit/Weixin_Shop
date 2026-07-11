#!/usr/bin/env bash
set -u

project_root="$(cd "$(dirname "$0")" && pwd)"
runtime_dir="${START_LOCAL_RUNTIME_DIR:-${project_root}/.local-run}"

if ! "${project_root}/scripts/start-local.sh"; then
  echo
  read -r -p "启动失败，按回车键关闭此窗口..." _
  exit 1
fi

backend_port="$(cat "${runtime_dir}/backend.port")"
trap 'exit 0' INT TERM HUP

echo
echo "请保持此窗口开启；关闭窗口将结束本地管理后台会话。"
echo "后台地址：http://localhost:5173"

while curl -fsS --max-time 2 "http://localhost:${backend_port}/actuator/health" >/dev/null 2>&1 \
  && curl -fsS --max-time 2 "http://localhost:5173/" >/dev/null 2>&1; do
  sleep 3
done

echo
echo "检测到本地服务已经停止。日志位于 ${runtime_dir}/"
read -r -p "按回车键关闭此窗口..." _
exit 1
