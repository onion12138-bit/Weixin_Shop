#!/usr/bin/env bash
set -euo pipefail

# 汇总所有环境问题后一次性返回，避免开发者逐个修复、反复执行。
failures=0

require_command() {
  local command_name="$1"
  local install_hint="$2"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "缺少 ${command_name}：${install_hint}" >&2
    failures=$((failures + 1))
  fi
}

require_minimum_major() {
  local display_name="$1"
  local actual_major="$2"
  local required_major="$3"

  if [[ -z "${actual_major}" || "${actual_major}" -lt "${required_major}" ]]; then
    echo "${display_name} 主版本必须不低于 ${required_major}，当前为 ${actual_major:-未知}" >&2
    failures=$((failures + 1))
  fi
}

require_command java "安装 JDK 17 并执行 source ./scripts/project-env.sh"
require_command mvn "确认项目根目录存在 apache-maven-3.9.16 并加载项目环境"
require_command node "安装 Node.js 24 LTS，或至少 22.13 的 Node.js 22"
require_command pnpm "通过 corepack 启用项目锁定的 pnpm 11.7.0"
require_command docker "启动 Docker Desktop 并加载项目环境"

if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | awk -F '[".]' '/version/ {print ($2 == 1 ? $3 : $2); exit}')"
  require_minimum_major "JDK" "${java_major}" 17
fi

if command -v mvn >/dev/null 2>&1; then
  maven_major="$(mvn -version | awk 'NR == 1 {split($3, parts, "."); print parts[1]}')"
  require_minimum_major "Maven" "${maven_major}" 3
fi

if command -v node >/dev/null 2>&1; then
  # 与前端 engines 保持一致：Node 23 不受测试工具支持，不能仅判断主版本下限。
  if ! node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit((major === 22 && minor >= 13) || major >= 24 ? 0 : 1)'; then
    echo "Node.js 版本不兼容，请使用 24 LTS 或 >=22.13 的 22.x。" >&2
    failures=$((failures + 1))
  fi
fi

if command -v pnpm >/dev/null 2>&1; then
  pnpm_major="$(pnpm --version | awk -F '.' '{print $1}')"
  require_minimum_major "pnpm" "${pnpm_major}" 11
fi

if command -v docker >/dev/null 2>&1; then
  if ! docker info >/dev/null 2>&1; then
    echo "Docker CLI 已安装，但 Docker Desktop 引擎尚未运行。" >&2
    failures=$((failures + 1))
  fi

  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose 插件不可用。" >&2
    failures=$((failures + 1))
  fi
fi

if [[ "${failures}" -ne 0 ]]; then
  echo "环境检查失败：${failures} 项不满足要求。" >&2
  exit 1
fi

echo "环境检查通过。"
