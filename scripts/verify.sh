#!/usr/bin/env bash
set -euo pipefail

# 统一从脚本位置解析项目根目录，允许从任意当前目录执行本脚本。
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 自动启用项目锁定的 JDK、Maven 与 Docker CLI，避免依赖用户全局 PATH。
source "${project_root}/scripts/project-env.sh"

run_node_script() {
  local script_name="$1"
  if command -v pnpm >/dev/null 2>&1; then
    pnpm "${script_name}"
    return
  fi
  if [[ "${script_name}" == "test" ]]; then
    npm test
    return
  fi
  npm run "${script_name}"
}

echo "[1/4] 后端测试"
(cd "${project_root}/backend" && mvn test)

echo "[2/4] 管理后台测试与构建"
(cd "${project_root}/admin-web" && run_node_script test && run_node_script build)

echo "[3/4] 小程序类型检查与逻辑测试"
(cd "${project_root}/miniprogram" && run_node_script typecheck && run_node_script test)

echo "[4/4] 冒烟脚本静态契约"
bash -n "${project_root}/scripts/diy-visual-smoke.sh"
grep -Fq '/visuals/manual' "${project_root}/scripts/diy-visual-smoke.sh"
grep -Fq '/publish' "${project_root}/scripts/diy-visual-smoke.sh"
grep -Fq 'diyVisual' "${project_root}/scripts/diy-visual-smoke.sh"

bash -n "${project_root}/scripts/recommendation-smoke.sh"
bash "${project_root}/scripts/recommendation-smoke.test.sh"
grep -Fq 'DEEPSEEK_API_KEY' "${project_root}/scripts/recommendation-smoke.sh"
grep -Fq 'AI_CONFIG_ENCRYPTION_KEY' "${project_root}/scripts/recommendation-smoke.sh"
grep -Fq '/api/app/recommendations/status' "${project_root}/scripts/recommendation-smoke.sh"
grep -Fq '/api/app/recommendations' "${project_root}/scripts/recommendation-smoke.sh"
grep -Fq '/api/admin/ai/config/test' "${project_root}/scripts/recommendation-smoke.sh"
grep -Fq '/api/public/catalog/beads?page=$catalog_page&size=$catalog_page_size' \
  "${project_root}/scripts/recommendation-smoke.sh"

echo "全仓验证通过。"
