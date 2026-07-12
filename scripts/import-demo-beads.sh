#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
asset_dir="$root_dir/assets/demo-catalog"
backend_base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
backend_base_url="${backend_base_url%/}"
admin_username="${ADMIN_USERNAME:-admin}"
admin_password="${ADMIN_PASSWORD:-Admin@123}"

for required_command in curl jq; do
  command -v "$required_command" >/dev/null 2>&1 || {
    printf 'ERROR: %s is required\n' "$required_command" >&2
    exit 1
  }
done

login_payload="$(mktemp)"
chmod 600 "$login_payload"
trap 'rm -f "$login_payload"' EXIT
jq -cn \
  --arg username "$admin_username" \
  --arg password "$admin_password" \
  '{username:$username,password:$password}' > "$login_payload"

api_call() {
  local operation="$1"
  shift
  local response
  if ! response="$(curl -fsS "$@")"; then
    printf 'ERROR: %s failed\n' "$operation" >&2
    return 1
  fi
  if ! jq -e '.code == "OK"' >/dev/null 2>&1 <<< "$response"; then
    printf 'ERROR: %s returned API error: %s\n' \
      "$operation" \
      "$(jq -r '(.code // "UNKNOWN") + ": " + (.message // "unknown error")' <<< "$response" 2>/dev/null || printf 'invalid JSON response')" >&2
    return 1
  fi
  printf '%s' "$response"
}

login_response="$(api_call \
  'administrator login' \
  -H 'Content-Type: application/json' \
  --data-binary "@$login_payload" \
  "$backend_base_url/api/admin/auth/login")"
access_token="$(jq -er '.data.accessToken | select(type == "string" and length > 0)' <<< "$login_response")" || {
  printf 'ERROR: administrator login response did not contain an access token\n' >&2
  exit 1
}
auth_header="Authorization: Bearer $access_token"

category_response="$(api_call \
  'list administrator categories' \
  -H "$auth_header" \
  "$backend_base_url/api/admin/catalog/categories")"
if ! jq -e '.data[] | select(.id == 1001)' >/dev/null 2>&1 <<< "$category_response"; then
  printf 'ERROR: required category 1001 (天然水晶) does not exist\n' >&2
  exit 1
fi

beads_response="$(api_call \
  'list administrator beads' \
  -H "$auth_header" \
  "$backend_base_url/api/admin/catalog/beads?page=1&size=100")"
existing_skus="$(jq -c '[.data.items[].skuCode]' <<< "$beads_response")"

while IFS='|' read -r sku name material color sale_price cost_price tags_json image_name; do
  if jq -e --arg sku "$sku" 'index($sku) != null' >/dev/null 2>&1 <<< "$existing_skus"; then
    printf 'skipped existing SKU: %s\n' "$sku"
    continue
  fi

  image_path="$asset_dir/$image_name"
  [[ -s "$image_path" ]] || {
    printf 'ERROR: cover image missing or empty for %s: %s\n' "$sku" "$image_path" >&2
    exit 1
  }

  upload_response="$(api_call \
    "upload cover for $sku" \
    -H "$auth_header" \
    -F "file=@$image_path;type=image/png" \
    "$backend_base_url/api/admin/files")"
  cover_asset_id="$(jq -er '.data.id | select(type == "number" and . > 0)' <<< "$upload_response")" || {
    printf 'ERROR: upload response did not contain a cover asset id for %s\n' "$sku" >&2
    exit 1
  }

  payload="$(jq -cn \
    --arg skuCode "$sku" \
    --arg name "$name" \
    --arg material "$material" \
    --arg color "$color" \
    --argjson salePrice "$sale_price" \
    --argjson costPrice "$cost_price" \
    --argjson coverAssetId "$cover_asset_id" \
    --argjson tags "$tags_json" \
    '{
      categoryId:1001,
      skuCode:$skuCode,
      name:$name,
      material:$material,
      color:$color,
      diameterMm:8,
      salePrice:$salePrice,
      costPrice:$costPrice,
      stockAlertThreshold:20,
      coverAssetId:$coverAssetId,
      tags:$tags,
      status:"ON_SALE",
      initialStock:200
    }')"

  api_call \
    "create bead $sku" \
    -H "$auth_header" \
    -H 'Content-Type: application/json' \
    --data-binary "$payload" \
    "$backend_base_url/api/admin/catalog/beads" >/dev/null
  existing_skus="$(jq -c --arg sku "$sku" '. + [$sku]' <<< "$existing_skus")"
  printf 'created SKU: %s\n' "$sku"
done <<'PRODUCTS'
BEAD-LABRADORITE-8MM|拉长石 8mm|天然水晶|灰蓝色|9.80|2.80|["灵感","守护","灰蓝","通勤"]|labradorite-8mm.png
BEAD-GARNET-8MM|石榴石 8mm|天然水晶|酒红色|9.50|2.50|["活力","热情","红色","日常"]|garnet-8mm.png
BEAD-FLUORITE-8MM|萤石 8mm|天然水晶|紫绿色|8.80|1.80|["专注","平衡","紫绿","学习"]|fluorite-8mm.png
PRODUCTS
