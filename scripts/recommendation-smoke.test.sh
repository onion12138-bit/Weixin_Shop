#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
smoke_script="$project_root/scripts/recommendation-smoke.sh"
uuid_regex='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'

function_source="$(sed -n '/^new_installation_id() {$/,/^}$/p' "$smoke_script")"
[[ -n "$function_source" ]] || {
  printf 'FAIL: new_installation_id function is missing\n' >&2
  exit 1
}
eval "$function_source"

validation_source="$(sed -n '/^validate_live_recommendation() {$/,/^}$/p' "$smoke_script")"
[[ -n "$validation_source" ]] || {
  printf 'FAIL: validate_live_recommendation function is missing\n' >&2
  exit 1
}
eval "$validation_source"

# 固定最大 16-bit 输入证明版本段和 variant 后 12-bit 必须被截断。
fixed_uuid="$(printf '%04x%04x-%04x-4%03x-%x%03x-%04x%04x%04x' \
  65535 65535 65535 "$((65535 & 0x0fff))" \
  11 "$((65535 & 0x0fff))" 65535 65535 65535)"
[[ "$fixed_uuid" =~ $uuid_regex ]] || {
  printf 'FAIL: fixed UUID sample does not match the guest-session contract: %s\n' \
    "$fixed_uuid" >&2
  exit 1
}

for ((iteration = 1; iteration <= 512; iteration += 1)); do
  generated_uuid="$(new_installation_id)"
  [[ "$generated_uuid" =~ $uuid_regex ]] || {
    printf 'FAIL: generated UUID does not match the guest-session contract: %s\n' \
      "$generated_uuid" >&2
    exit 1
  }
done

plan_count=2
catalog_ids='[2001,2002,2003]'
valid_recommendation='{
  "code":"OK",
  "data":{
    "profile":{
      "birthDate":"1992-01-20",
      "constellation":"水瓶座",
      "zodiac":"猴",
      "constellationElement":"风象"
    },
    "plans":[
      {
        "schemeName":"蓝光启程",
        "summary":"蓝色点缀",
        "items":[
          {"skuId":2001,"name":"青金石","imageUrl":"http://localhost:8081/api/files/1","reason":"深蓝层次"},
          {"skuId":2002,"name":"月光石","imageUrl":"http://localhost:8081/api/files/2","reason":"清透过渡"},
          {"skuId":2003,"name":"拉长石","imageUrl":"http://localhost:8081/api/files/3","reason":"灰蓝衔接"}
        ]
      },
      {
        "schemeName":"自然焕新",
        "summary":"日常清爽",
        "items":[
          {"skuId":2003,"name":"拉长石","imageUrl":"http://localhost:8081/api/files/3","reason":"沉稳基调"},
          {"skuId":2001,"name":"青金石","imageUrl":"http://localhost:8081/api/files/1","reason":"蓝色点睛"},
          {"skuId":2002,"name":"月光石","imageUrl":"http://localhost:8081/api/files/2","reason":"柔和收束"}
        ]
      }
    ],
    "culturalDisclaimer":"仅供文化娱乐参考"
  }
}'
validate_live_recommendation "$valid_recommendation" "$catalog_ids" || {
  printf 'FAIL: valid recommendation did not satisfy the catalog contract\n' >&2
  exit 1
}

foreign_sku_recommendation="$(jq -c \
  '.data.plans[0].items[2].skuId = 9999' \
  <<< "$valid_recommendation")"
if validate_live_recommendation "$foreign_sku_recommendation" "$catalog_ids"; then
  printf 'FAIL: recommendation accepted an item SKU absent from the catalog\n' >&2
  exit 1
fi

duplicate_sku_recommendation="$(jq -c \
  '.data.plans[0].items[2].skuId = 2001' \
  <<< "$valid_recommendation")"
if validate_live_recommendation "$duplicate_sku_recommendation" "$catalog_ids"; then
  printf 'FAIL: recommendation accepted a duplicate SKU in one plan\n' >&2
  exit 1
fi

grep -Fq -- '--connect-timeout' "$smoke_script" || {
  printf 'FAIL: recommendation smoke curl has no connection timeout\n' >&2
  exit 1
}
grep -Fq -- '--max-time' "$smoke_script" || {
  printf 'FAIL: recommendation smoke curl has no overall timeout\n' >&2
  exit 1
}

printf 'PASS: UUID, multi-plan SKU, duplicate, and curl-timeout contracts are enforced\n'
