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

printf 'PASS: 512 generated installation UUIDs match the backend contract\n'
