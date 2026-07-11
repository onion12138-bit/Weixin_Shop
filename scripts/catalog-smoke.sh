#!/usr/bin/env bash
set -euo pipefail

# 对已经启动的真实后端执行最小产品链路检查。该脚本不创建或修改业务数据，
# 因此可以在本地重复执行，也可以作为后续部署流水线的就绪探针。
base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
admin_username="${ADMIN_USERNAME:-admin}"
admin_password="${ADMIN_PASSWORD:-Admin@123}"
login_payload="$(node -e '
  process.stdout.write(JSON.stringify({
    username: process.argv[1],
    password: process.argv[2],
  }))
' "${admin_username}" "${admin_password}")"

health="$(curl -fsS "${base_url}/actuator/health")"
catalog="$(curl -fsS \
  "${base_url}/api/public/catalog/beads?page=1&size=20")"
login="$(curl -fsS \
  -H "Content-Type: application/json" \
  -d "${login_payload}" \
  "${base_url}/api/admin/auth/login")"
unauthorized_status="$(curl -sS \
  -o /dev/null \
  -w "%{http_code}" \
  "${base_url}/api/admin/catalog/categories")"

node -e '
const health = JSON.parse(process.argv[1])
const catalog = JSON.parse(process.argv[2])
const login = JSON.parse(process.argv[3])
const unauthorizedStatus = process.argv[4]

if (health.status !== "UP") {
  throw new Error("backend health is not UP")
}
if (catalog.code !== "OK") {
  throw new Error(`catalog API failed: ${catalog.code}`)
}
if (!Array.isArray(catalog.data?.items) || catalog.data.items.length < 4) {
  throw new Error("catalog seed data must contain at least four visible beads")
}
if (login.code !== "OK" || !login.data?.accessToken) {
  throw new Error("admin login did not return an access token")
}
if (unauthorizedStatus !== "401") {
  throw new Error(`admin API without token returned ${unauthorizedStatus}`)
}
' "${health}" "${catalog}" "${login}" "${unauthorized_status}"

echo "商品目录真实 HTTP 冒烟通过：健康、公开目录、管理员登录和未认证保护均正常。"
