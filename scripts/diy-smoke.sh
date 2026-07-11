#!/usr/bin/env bash
set -euo pipefail

# 对真实后端执行 DIY 方案闭环检查。脚本只依赖 curl 和 node：
# - curl 负责真实 HTTP 请求；
# - node 负责生成 UUID、构造 JSON 和断言响应结构，避免额外引入 jq。
base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
installation_id="$(node -e 'process.stdout.write(crypto.randomUUID())')"
sequence_json="$(node -e '
const sequence = [
  ...Array(12).fill(2001),
  ...Array(9).fill(2002),
]
process.stdout.write(JSON.stringify(sequence))
')"

session_payload="$(node -e '
process.stdout.write(JSON.stringify({ installationId: process.argv[1] }))
' "${installation_id}")"
session="$(curl -fsS \
  -H "Content-Type: application/json" \
  -d "${session_payload}" \
  "${base_url}/api/app/session/guest")"
customer_token="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.accessToken) {
  throw new Error("guest session did not return an access token")
}
process.stdout.write(response.data.accessToken)
' "${session}")"

config="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  "${base_url}/api/app/diy/config")"
quote_payload="$(node -e '
process.stdout.write(JSON.stringify({
  wristCircumferenceMm: 160,
  fitType: "STANDARD",
  cordOptionId: 6001,
  sequence: JSON.parse(process.argv[1]),
}))
' "${sequence_json}")"
quote="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -H "Content-Type: application/json" \
  -d "${quote_payload}" \
  "${base_url}/api/app/diy/quotes")"

scheme_payload="$(node -e '
process.stdout.write(JSON.stringify({
  name: `冒烟方案 ${new Date().toISOString()}`,
  wristCircumferenceMm: 160,
  fitType: "STANDARD",
  cordOptionId: 6001,
  sequence: JSON.parse(process.argv[1]),
  version: 0,
}))
' "${sequence_json}")"
scheme="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -H "Content-Type: application/json" \
  -d "${scheme_payload}" \
  "${base_url}/api/app/diy/schemes")"
scheme_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id) {
  throw new Error("scheme creation did not return an id")
}
process.stdout.write(String(response.data.id))
' "${scheme}")"

fetched="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  "${base_url}/api/app/diy/schemes/${scheme_id}")"
copy="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -X POST \
  "${base_url}/api/app/diy/schemes/${scheme_id}/copies")"
copy_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id) {
  throw new Error("scheme copy did not return an id")
}
process.stdout.write(String(response.data.id))
' "${copy}")"
deleted_copy="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -X DELETE \
  "${base_url}/api/app/diy/schemes/${copy_id}")"

node -e '
const config = JSON.parse(process.argv[1])
const quote = JSON.parse(process.argv[2])
const scheme = JSON.parse(process.argv[3])
const fetched = JSON.parse(process.argv[4])
const copy = JSON.parse(process.argv[5])
const deletedCopy = JSON.parse(process.argv[6])
const sequence = JSON.parse(process.argv[7])

if (config.code !== "OK" || !config.data?.rule || !config.data?.cordOptions?.length) {
  throw new Error("DIY config is not usable")
}
if (quote.code !== "OK" || quote.data?.quotable !== true) {
  throw new Error("DIY quote is not usable")
}
if (quote.data.totalAmount !== 314.8) {
  throw new Error(`unexpected total: ${quote.data.totalAmount}`)
}
if (
  scheme.code !== "OK" ||
  scheme.data?.sequence?.join(",") !== sequence.join(",")
) {
  throw new Error("saved scheme did not preserve SKU order")
}
if (
  fetched.code !== "OK" ||
  fetched.data?.id !== scheme.data.id ||
  fetched.data?.sequence?.join(",") !== sequence.join(",")
) {
  throw new Error("read scheme did not preserve SKU order")
}
if (
  copy.code !== "OK" ||
  copy.data?.id === scheme.data.id ||
  copy.data?.sequence?.join(",") !== sequence.join(",")
) {
  throw new Error("copied scheme is not a valid independent copy")
}
if (deletedCopy.code !== "OK" || deletedCopy.data !== true) {
  throw new Error("copied scheme was not deleted")
}
' "${config}" "${quote}" "${scheme}" "${fetched}" "${copy}" "${deleted_copy}" "${sequence_json}"

echo "DIY 真实 HTTP 冒烟通过：访客身份、配置、报价、保存、读取、复制和删除副本均正常。"
