#!/usr/bin/env bash
set -euo pipefail

# 对已启动的真实后端执行订单与履约闭环检查。脚本只依赖 curl 和 node，
# 不调用微信支付或真实物流；支付渠道与运单均为本地演示数据。
base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
admin_username="${ADMIN_USERNAME:-admin}"
admin_password="${ADMIN_PASSWORD:-Admin@123}"
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

scheme_payload="$(node -e '
process.stdout.write(JSON.stringify({
  name: `订单冒烟方案 ${new Date().toISOString()}`,
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

order_payload="$(node -e '
process.stdout.write(JSON.stringify({
  schemeId: Number(process.argv[1]),
  contactName: "订单冒烟顾客",
  contactPhone: "13800000000",
  addressDetail: "上海市演示区真实接口路 1 号",
}))
' "${scheme_id}")"
order="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -H "Content-Type: application/json" \
  -d "${order_payload}" \
  "${base_url}/api/app/orders")"
order_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (
  response.code !== "OK" ||
  response.data?.status !== "PENDING_PAYMENT" ||
  !response.data?.id
) {
  throw new Error("order creation did not return a pending order")
}
process.stdout.write(String(response.data.id))
' "${order}")"

customer_orders="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  "${base_url}/api/app/orders")"
customer_order_detail="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  "${base_url}/api/app/orders/${order_id}")"
paid_once="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -X POST \
  "${base_url}/api/app/orders/${order_id}/mock-pay")"
paid_twice="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -X POST \
  "${base_url}/api/app/orders/${order_id}/mock-pay")"

login_payload="$(node -e '
process.stdout.write(JSON.stringify({
  username: process.argv[1],
  password: process.argv[2],
}))
' "${admin_username}" "${admin_password}")"
login="$(curl -fsS \
  -H "Content-Type: application/json" \
  -d "${login_payload}" \
  "${base_url}/api/admin/auth/login")"
admin_token="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.accessToken) {
  throw new Error("admin login did not return an access token")
}
process.stdout.write(response.data.accessToken)
' "${login}")"

admin_orders="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  "${base_url}/api/admin/orders?status=PAID&page=1&size=100")"
production="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -X POST \
  "${base_url}/api/admin/orders/${order_id}/start-production")"
ship_payload='{"logisticsCompany":"顺丰速运","trackingNo":"SF-ORDER-SMOKE-001"}'
shipped="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -H "Content-Type: application/json" \
  -d "${ship_payload}" \
  "${base_url}/api/admin/orders/${order_id}/ship")"
completed="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -X POST \
  "${base_url}/api/admin/orders/${order_id}/complete")"
completed_for_customer="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  "${base_url}/api/app/orders/${order_id}")"

# 创建第二笔订单后取消，验证预占释放分支。使用同一方案即可，因为订单会保存独立快照。
cancel_order="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -H "Content-Type: application/json" \
  -d "${order_payload}" \
  "${base_url}/api/app/orders")"
cancel_order_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id) {
  throw new Error("cancel branch order creation failed")
}
process.stdout.write(String(response.data.id))
' "${cancel_order}")"
cancelled="$(curl -fsS \
  -H "Authorization: Bearer ${customer_token}" \
  -X POST \
  "${base_url}/api/app/orders/${cancel_order_id}/cancel")"

node -e '
const orderId = Number(process.argv[1])
const order = JSON.parse(process.argv[2])
const customerOrders = JSON.parse(process.argv[3])
const customerDetail = JSON.parse(process.argv[4])
const paidOnce = JSON.parse(process.argv[5])
const paidTwice = JSON.parse(process.argv[6])
const adminOrders = JSON.parse(process.argv[7])
const production = JSON.parse(process.argv[8])
const shipped = JSON.parse(process.argv[9])
const completed = JSON.parse(process.argv[10])
const customerCompleted = JSON.parse(process.argv[11])
const cancelled = JSON.parse(process.argv[12])

if (order.data?.totalAmount !== 314.8 || order.data?.items?.length !== 2) {
  throw new Error("order did not preserve authoritative amount and BOM")
}
if (!customerOrders.data?.some((item) => item.id === orderId)) {
  throw new Error("customer order list does not contain the created order")
}
if (customerDetail.data?.id !== orderId) {
  throw new Error("customer order detail is not readable")
}
if (paidOnce.data?.status !== "PAID" || paidTwice.data?.status !== "PAID") {
  throw new Error("mock payment is not successful and idempotent")
}
if (!adminOrders.data?.items?.some((item) => item.id === orderId)) {
  throw new Error("paid order is not visible to administrator")
}
if (production.data?.status !== "IN_PRODUCTION") {
  throw new Error("order did not enter production")
}
if (
  shipped.data?.status !== "SHIPPED" ||
  shipped.data?.fulfillment?.trackingNo !== "SF-ORDER-SMOKE-001"
) {
  throw new Error("order shipment did not preserve tracking information")
}
if (
  completed.data?.status !== "COMPLETED" ||
  customerCompleted.data?.status !== "COMPLETED"
) {
  throw new Error("completed status is not visible to both clients")
}
if (cancelled.data?.status !== "CANCELLED") {
  throw new Error("pending order cancellation failed")
}
' "${order_id}" "${order}" "${customer_orders}" "${customer_order_detail}" \
  "${paid_once}" "${paid_twice}" "${admin_orders}" "${production}" \
  "${shipped}" "${completed}" "${completed_for_customer}" "${cancelled}"

echo "订单真实 HTTP 冒烟通过：下单、本人查询、幂等模拟支付、制作、发货、完成和取消均正常。"
