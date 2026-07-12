#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
asset_dir="$root_dir/assets/demo-catalog"
importer="$root_dir/scripts/import-demo-beads.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_png() {
  local path="$1"
  [[ -s "$path" ]] || fail "missing or empty asset: $path"
  file -b "$path" | grep -q '^PNG image data' || fail "not a PNG: $path"
}

cover_assets=(
  "$asset_dir/labradorite-8mm.png"
  "$asset_dir/garnet-8mm.png"
  "$asset_dir/fluorite-8mm.png"
)

for asset in "${cover_assets[@]}" "$asset_dir/ai-water-guide.png"; do
  assert_png "$asset"
done

expected_dimensions=""
for asset in "${cover_assets[@]}"; do
  dimensions="$(sips -g pixelWidth -g pixelHeight "$asset" 2>/dev/null | awk '/pixelWidth:/{width=$2} /pixelHeight:/{height=$2} END{print width "x" height}')"
  [[ -n "$dimensions" ]] || fail "could not read dimensions: $asset"
  if [[ -z "$expected_dimensions" ]]; then
    expected_dimensions="$dimensions"
  elif [[ "$dimensions" != "$expected_dimensions" ]]; then
    fail "cover dimensions differ: expected $expected_dimensions, got $dimensions for $asset"
  fi
done

guide_dimensions="$(sips -g pixelWidth -g pixelHeight "$asset_dir/ai-water-guide.png" 2>/dev/null | awk '/pixelWidth:/{width=$2} /pixelHeight:/{height=$2} END{print width "x" height}')"
[[ "$guide_dimensions" == "512x512" ]] || fail "AI entrance image must be 512x512, got $guide_dimensions"

[[ -f "$importer" ]] || fail "missing importer: $importer"

fake_dir="$(mktemp -d)"
trap 'rm -rf "$fake_dir"' EXIT
curl_log="$fake_dir/curl.log"
created_skus="$fake_dir/created-skus"

cat > "$fake_dir/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail

printf '<%s>\n' "$@" >> "$FAKE_CURL_LOG"

url="${!#}"
if [[ -n "${FAKE_CURL_FAIL_URL:-}" && "$url" == *"$FAKE_CURL_FAIL_URL"* ]]; then
  printf 'simulated curl failure for %s\n' "$url" >&2
  exit 22
fi
if [[ -n "${FAKE_API_ERROR_URL:-}" && "$url" == *"$FAKE_API_ERROR_URL"* ]]; then
  printf '%s\n' '{"code":"CATALOG_FAKE_ERROR","message":"simulated API envelope failure","data":null}'
  exit 0
fi
case "$url" in
  */api/admin/auth/login)
    printf '%s\n' '{"code":"OK","message":"success","data":{"accessToken":"fake-admin-token","tokenType":"Bearer","expiresInSeconds":3600}}'
    ;;
  */api/admin/catalog/categories)
    printf '%s\n' '{"code":"OK","message":"success","data":[{"id":1001,"name":"天然水晶","slug":"natural-crystal","sortOrder":1,"status":"ON_SALE"}]}'
    ;;
  */api/admin/catalog/beads?page=1\&size=100)
    case "$FAKE_CATALOG_SCENARIO" in
      empty)
        items="$(jq -Rsc 'split("\n") | map(select(length > 0) | {skuCode:.})' "$FAKE_CREATED_SKUS")"
        jq -cn --argjson items "$items" \
          '{code:"OK",message:"success",data:{items:$items,total:($items | length),page:1,size:100}}'
        ;;
      paginated)
        items="$(jq -cn '[range(1; 101) | {skuCode:("DUMMY-" + tostring)}]')"
        jq -cn --argjson items "$items" \
          '{code:"OK",message:"success",data:{items:$items,total:101,page:1,size:100}}'
        ;;
      *)
        printf 'unknown fake catalog scenario: %s\n' "$FAKE_CATALOG_SCENARIO" >&2
        exit 22
        ;;
    esac
    ;;
  */api/admin/catalog/beads?page=2\&size=100)
    [[ "$FAKE_CATALOG_SCENARIO" == "paginated" ]] || {
      printf 'unexpected page 2 request for scenario: %s\n' "$FAKE_CATALOG_SCENARIO" >&2
      exit 22
    }
    printf '%s\n' '{"code":"OK","message":"success","data":{"items":[{"skuCode":"BEAD-LABRADORITE-8MM"}],"total":101,"page":2,"size":100}}'
    ;;
  */api/admin/files)
    upload_count="$(grep -c '/api/admin/files' "$FAKE_CURL_LOG" || true)"
    printf '{"code":"OK","message":"success","data":{"id":%d,"storageKey":"demo/%d.png","originalName":"demo.png","contentType":"image/png","sizeBytes":100,"publicUrl":"/api/public/files/demo/%d.png"}}\n' "$((9000 + upload_count))" "$upload_count" "$upload_count"
    ;;
  */api/admin/catalog/beads)
    for argument in "$@"; do
      if [[ "$argument" == *'"skuCode"'* ]]; then
        jq -r '.skuCode' <<< "$argument" >> "$FAKE_CREATED_SKUS"
        break
      fi
    done
    printf '%s\n' '{"code":"OK","message":"success","data":{"id":2001}}'
    ;;
  *)
    printf 'fake curl received unexpected URL: %s\n' "$url" >&2
    exit 22
    ;;
esac
FAKE_CURL
chmod +x "$fake_dir/curl"

export PATH="$fake_dir:$PATH"
export FAKE_CURL_LOG="$curl_log"
export FAKE_CREATED_SKUS="$created_skus"
export BACKEND_BASE_URL="http://fake-backend:8081"
export ADMIN_USERNAME="admin-contract"
export ADMIN_PASSWORD="contract-password-must-stay-private"

reset_fake_state() {
  : > "$curl_log"
  : > "$created_skus"
  unset FAKE_CURL_FAIL_URL FAKE_API_ERROR_URL
}

# Scenario 1: an empty catalog creates all products once, then skips all three.
reset_fake_state
export FAKE_CATALOG_SCENARIO="empty"
first_output="$(bash "$importer" 2>&1)" || fail "first fake import failed: $first_output"
second_output="$(bash "$importer" 2>&1)" || fail "second fake import failed: $second_output"
combined_output="$first_output
$second_output"

[[ "$combined_output" != *"$ADMIN_PASSWORD"* ]] || fail "importer leaked the administrator password"

for sku in BEAD-LABRADORITE-8MM BEAD-GARNET-8MM BEAD-FLUORITE-8MM; do
  count="$(grep -o "\\\"skuCode\\\":\\\"$sku\\\"" "$curl_log" | wc -l | tr -d ' ')"
  [[ "$count" == "1" ]] || fail "expected one create payload for $sku, got $count"
  [[ "$first_output" == *"created SKU: $sku"* ]] || fail "first import did not create $sku"
  [[ "$second_output" == *"skipped existing SKU: $sku"* ]] || fail "second import did not skip $sku"
done

stock_count="$(grep -o '\"initialStock\":200' "$curl_log" | wc -l | tr -d ' ')"
[[ "$stock_count" == "3" ]] || fail "expected initialStock 200 in three create payloads, got $stock_count"

payloads_json="$(grep '^<{.*\"skuCode\"' "$curl_log" | sed 's/^<//; s/>$//' | jq -s '.')"
if ! jq -e '
  map(del(.coverAssetId)) == [
    {categoryId:1001,skuCode:"BEAD-LABRADORITE-8MM",name:"拉长石 8mm",material:"天然水晶",color:"灰蓝色",diameterMm:8,salePrice:9.80,costPrice:2.80,stockAlertThreshold:20,tags:["灵感","守护","灰蓝","通勤"],status:"ON_SALE",initialStock:200},
    {categoryId:1001,skuCode:"BEAD-GARNET-8MM",name:"石榴石 8mm",material:"天然水晶",color:"酒红色",diameterMm:8,salePrice:9.50,costPrice:2.50,stockAlertThreshold:20,tags:["活力","热情","红色","日常"],status:"ON_SALE",initialStock:200},
    {categoryId:1001,skuCode:"BEAD-FLUORITE-8MM",name:"萤石 8mm",material:"天然水晶",color:"紫绿色",diameterMm:8,salePrice:8.80,costPrice:1.80,stockAlertThreshold:20,tags:["专注","平衡","紫绿","学习"],status:"ON_SALE",initialStock:200}
  ]
' >/dev/null <<< "$payloads_json"; then
  fail "create payloads do not match the exact demo product table"
fi

upload_count="$(grep -c '/api/admin/files' "$curl_log")"
[[ "$upload_count" == "3" ]] || fail "expected three uploads across two runs, got $upload_count"

# Scenario 2: page 2 contains Labradorite, which must be found before uploads.
reset_fake_state
export FAKE_CATALOG_SCENARIO="paginated"
paginated_output="$(bash "$importer" 2>&1)" || fail "paginated fake import failed: $paginated_output"
[[ "$paginated_output" == *"skipped existing SKU: BEAD-LABRADORITE-8MM"* ]] || fail "paginated import did not discover the second-page SKU"
if grep -q 'labradorite-8mm.png' "$curl_log"; then
  fail "second-page SKU was uploaded before being skipped"
fi
if grep -q '\"skuCode\":\"BEAD-LABRADORITE-8MM\"' "$curl_log"; then
  fail "second-page SKU was submitted for creation"
fi
page_two_line="$(awk '/\/api\/admin\/catalog\/beads\?page=2&size=100/{print NR; exit}' "$curl_log")"
first_upload_line="$(awk '/\/api\/admin\/files/{print NR; exit}' "$curl_log")"
[[ -n "$page_two_line" && -n "$first_upload_line" && "$page_two_line" -lt "$first_upload_line" ]] || fail "page 2 was not read before the first upload"

# Failure scenarios use fresh state so their logs cannot inherit product data.
reset_fake_state
export FAKE_CATALOG_SCENARIO="empty"
export FAKE_CURL_FAIL_URL="/api/admin/auth/login"
if failure_output="$(bash "$importer" 2>&1)"; then
  fail "importer succeeded despite a curl login failure"
fi
[[ "$failure_output" == *"ERROR: administrator login failed"* ]] || fail "curl failure did not identify the failed operation"
[[ "$failure_output" != *"$ADMIN_PASSWORD"* ]] || fail "curl failure output leaked the administrator password"

reset_fake_state
export FAKE_CATALOG_SCENARIO="empty"
export FAKE_API_ERROR_URL="/api/admin/catalog/categories"
if envelope_output="$(bash "$importer" 2>&1)"; then
  fail "importer succeeded despite a non-OK API envelope"
fi
[[ "$envelope_output" == *"ERROR: list administrator categories returned API error: CATALOG_FAKE_ERROR: simulated API envelope failure"* ]] || fail "API envelope failure did not identify the operation and server error"
[[ "$envelope_output" != *"$ADMIN_PASSWORD"* ]] || fail "API envelope failure output leaked the administrator password"

printf 'PASS: demo assets and idempotent importer contract\n'
