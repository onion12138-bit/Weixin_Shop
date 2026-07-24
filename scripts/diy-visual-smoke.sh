#!/usr/bin/env bash
set -euo pipefail

# This is deliberately opt-in: it uploads two caller-supplied transparent assets,
# creates a new manual visual version and publishes it. It never updates commerce
# fields or deletes data, but publishing changes the SKU's active DIY visual.
: "${DIY_VISUAL_SMOKE_SKU:?set the on-sale smoke SKU code}"
: "${DIY_VISUAL_TRAY_FILE:?set a 160x160 transparent PNG/WebP}"
: "${DIY_VISUAL_CANVAS_FILE:?set a 512x512 transparent PNG/WebP}"

base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
admin_username="${ADMIN_USERNAME:-admin}"
admin_password="${ADMIN_PASSWORD:-Admin@123}"

[[ -f "${DIY_VISUAL_TRAY_FILE}" ]] || {
  echo "DIY_VISUAL_TRAY_FILE is not a readable file: ${DIY_VISUAL_TRAY_FILE}" >&2
  exit 1
}
[[ -f "${DIY_VISUAL_CANVAS_FILE}" ]] || {
  echo "DIY_VISUAL_CANVAS_FILE is not a readable file: ${DIY_VISUAL_CANVAS_FILE}" >&2
  exit 1
}

login_payload="$(node -e '
process.stdout.write(JSON.stringify({ username: process.argv[1], password: process.argv[2] }))
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

encoded_sku="$(node -e 'process.stdout.write(encodeURIComponent(process.argv[1]))' "${DIY_VISUAL_SMOKE_SKU}")"
beads="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  "${base_url}/api/admin/catalog/beads?keyword=${encoded_sku}&page=1&size=100")"
sku_id="$(node -e '
const response = JSON.parse(process.argv[1])
const skuCode = process.argv[2]
const bead = response.data?.items?.find((item) => item.skuCode === skuCode)
if (response.code !== "OK" || !bead?.id) {
  throw new Error(`configured smoke SKU was not found: ${skuCode}`)
}
if (bead.status !== "ON_SALE") {
  throw new Error(`configured smoke SKU is not on sale: ${skuCode}`)
}
process.stdout.write(String(bead.id))
' "${beads}" "${DIY_VISUAL_SMOKE_SKU}")"

tray_upload="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -F "file=@${DIY_VISUAL_TRAY_FILE}" \
  "${base_url}/api/admin/files")"
canvas_upload="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -F "file=@${DIY_VISUAL_CANVAS_FILE}" \
  "${base_url}/api/admin/files")"
tray_asset_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id) throw new Error("tray upload did not return an asset id")
process.stdout.write(String(response.data.id))
' "${tray_upload}")"
canvas_asset_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id) throw new Error("canvas upload did not return an asset id")
process.stdout.write(String(response.data.id))
' "${canvas_upload}")"

manual_payload="$(node -e '
process.stdout.write(JSON.stringify({ trayAssetId: Number(process.argv[1]), canvasAssetId: Number(process.argv[2]) }))
' "${tray_asset_id}" "${canvas_asset_id}")"
manual="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -H "Content-Type: application/json" \
  -d "${manual_payload}" \
  "${base_url}/api/admin/catalog/beads/${sku_id}/visuals/manual")"
version_id="$(node -e '
const response = JSON.parse(process.argv[1])
if (response.code !== "OK" || !response.data?.id || !response.data?.version) {
  throw new Error("manual visual creation did not return a version")
}
process.stdout.write(String(response.data.id))
' "${manual}")"

published="$(curl -fsS \
  -H "Authorization: Bearer ${admin_token}" \
  -X POST \
  "${base_url}/api/admin/catalog/beads/${sku_id}/visuals/${version_id}/publish")"
public_bead="$(curl -fsS "${base_url}/api/public/catalog/beads/${sku_id}")"

node -e '
const published = JSON.parse(process.argv[1])
const bead = JSON.parse(process.argv[2])
const publishedVisual = published.data
const diyVisual = bead.data?.diyVisual
if (published.code !== "OK" || publishedVisual?.status !== "PUBLISHED") {
  throw new Error("manual visual was not published")
}
if (bead.code !== "OK" || !diyVisual) {
  throw new Error("public bead did not expose diyVisual")
}
if (diyVisual.version !== publishedVisual.version) {
  throw new Error("public diyVisual.version does not match the published version")
}
if (!diyVisual.trayUrl || !diyVisual.canvasUrl) {
  throw new Error("public diyVisual is missing trayUrl or canvasUrl")
}
if (diyVisual.trayUrl !== publishedVisual.trayUrl || diyVisual.canvasUrl !== publishedVisual.canvasUrl) {
  throw new Error("public diyVisual URLs do not match the published version")
}
' "${published}" "${public_bead}"

echo "DIY 固定视觉真实 HTTP 冒烟通过：上传、手工版本、发布及公共 diyVisual 读取均正常。"
