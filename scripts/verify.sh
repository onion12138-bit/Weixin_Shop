#!/usr/bin/env bash
set -euo pipefail

# 统一从脚本位置解析项目根目录，允许从任意当前目录执行本脚本。
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 自动启用项目锁定的 JDK、Maven 与 Docker CLI，避免依赖用户全局 PATH。
source "${project_root}/scripts/project-env.sh"

echo "[1/3] 后端测试"
(cd "${project_root}/backend" && mvn test)

echo "[2/3] 管理后台测试与构建"
(cd "${project_root}/admin-web" && pnpm test && pnpm build)

echo "[3/3] 小程序类型检查与逻辑测试"
(cd "${project_root}/miniprogram" && pnpm typecheck && pnpm test)

echo "全仓验证通过。"
