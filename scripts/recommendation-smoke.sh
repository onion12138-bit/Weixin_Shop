#!/usr/bin/env bash
set -euo pipefail
set +x

# 对已经启动的真实后端验证 AI 推荐的两条明确路径：
# - 默认路径不需要 Provider 密钥，只断言“未配置/已停用”及对应 POST 错误；
# - 仅当显式提供 DEEPSEEK_API_KEY 时，才保存配置并访问 DeepSeek。
# 脚本不会输出 API Key、JWT、Authorization 头或完整失败响应。

for required_command in curl jq mktemp chmod rm; do
  command -v "$required_command" >/dev/null 2>&1 || {
    printf 'ERROR: %s is required\n' "$required_command" >&2
    exit 1
  }
done

base_url="${BACKEND_BASE_URL:-http://localhost:8081}"
base_url="${base_url%/}"
admin_username="${ADMIN_USERNAME:-admin}"
admin_password="${ADMIN_PASSWORD:-Admin@123}"
timeout_seconds="${RECOMMENDATION_TIMEOUT_SECONDS:-30}"
max_output_tokens="${RECOMMENDATION_MAX_OUTPUT_TOKENS:-2048}"
plan_count="${RECOMMENDATION_PLAN_COUNT:-3}"
recommendation_prompt='根据生日画像与顾客期望，从候选库存珠子中给出风格有区分的日常材质搭配，并说明每种材质的选择理由。'
preference_prompt='逐项查看候选珠子的名称、材质、颜色和全部标签，优先理解顾客明确提出的偏好；存在匹配候选时尽量在多个方案中体现，但不作为强制约束。'
catalog_page_size=100

if [[ ! "$timeout_seconds" =~ ^[0-9]+$ ]] \
    || (( timeout_seconds < 1 || timeout_seconds > 120 )); then
  printf 'ERROR: RECOMMENDATION_TIMEOUT_SECONDS must be an integer from 1 to 120\n' >&2
  exit 1
fi
if [[ ! "$max_output_tokens" =~ ^[0-9]+$ ]] \
    || (( max_output_tokens < 256 || max_output_tokens > 16384 )); then
  printf 'ERROR: RECOMMENDATION_MAX_OUTPUT_TOKENS must be an integer from 256 to 16384\n' >&2
  exit 1
fi
if [[ ! "$plan_count" =~ ^[0-9]+$ ]] \
    || (( plan_count < 1 || plan_count > 5 )); then
  printf 'ERROR: RECOMMENDATION_PLAN_COUNT must be an integer from 1 to 5\n' >&2
  exit 1
fi

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/recommendation-smoke.XXXXXX")"
chmod 700 "$temp_dir"
cleanup() {
  local exit_status=$?
  trap - EXIT INT TERM
  rm -rf -- "$temp_dir"
  exit "$exit_status"
}
trap cleanup EXIT INT TERM

HTTP_STATUS=''
RESPONSE_BODY=''
response_counter=0

new_installation_id() {
  printf '%04x%04x-%04x-4%03x-%x%03x-%04x%04x%04x' \
    "$RANDOM" "$RANDOM" "$RANDOM" "$((RANDOM & 0x0fff))" \
    "$((8 + RANDOM % 4))" "$((RANDOM & 0x0fff))" \
    "$RANDOM" "$RANDOM" "$RANDOM"
}

validate_live_recommendation() {
  local recommendation_json="$1"
  local public_catalog_ids="$2"

  jq -e \
    --argjson catalogIds "$public_catalog_ids" \
    --argjson planCount "$plan_count" '
    .code == "OK"
    and (.data | type == "object")
    and (.data.profile.birthDate == "1992-01-20")
    and (.data.profile.constellation | type == "string" and length > 0)
    and (.data.profile.zodiac | type == "string" and length > 0)
    and (.data.profile.constellationElement | type == "string" and length > 0)
    and (.data.plans | type == "array" and length == $planCount)
    and (all(.data.plans[];
      (.schemeName | type == "string" and length > 0)
      and (.summary | type == "string" and length > 0)
      and (.items | type == "array" and length >= 3 and length <= 5)
      and (([.items[].skuId] | length) == ([.items[].skuId] | unique | length))
      and (([.items[].skuId] | unique) - $catalogIds | length == 0)
      and (all(.items[];
        (.skuId | type == "number" and floor == . and . > 0)
        and (.name | type == "string" and length > 0)
        and (.imageUrl | type == "string" and length > 0)
        and (.reason | type == "string" and length > 0)))))
    and (.data.culturalDisclaimer | type == "string" and length > 0)
  ' >/dev/null 2>&1 <<< "$recommendation_json"
}

build_payload() {
  local payload_kind="$1"
  case "$payload_kind" in
    guest)
      SMOKE_INSTALLATION_ID="$installation_id" jq -cn \
        '{installationId:env.SMOKE_INSTALLATION_ID}'
      ;;
    admin-login)
      SMOKE_ADMIN_USERNAME="$admin_username" \
        SMOKE_ADMIN_PASSWORD="$admin_password" \
        jq -cn '{username:env.SMOKE_ADMIN_USERNAME,password:env.SMOKE_ADMIN_PASSWORD}'
      ;;
    save-config)
      SMOKE_CONFIG_VERSION="$config_version" \
        SMOKE_TIMEOUT_SECONDS="$timeout_seconds" \
        SMOKE_MAX_OUTPUT_TOKENS="$max_output_tokens" \
        SMOKE_PLAN_COUNT="$plan_count" \
        SMOKE_RECOMMENDATION_PROMPT="$recommendation_prompt" \
        SMOKE_PREFERENCE_PROMPT="$preference_prompt" \
        jq -cn '{
          baseUrl:"https://api.deepseek.com",
          modelName:"deepseek-v4-flash",
          apiKey:env.DEEPSEEK_API_KEY,
          enabled:true,
          timeoutSeconds:(env.SMOKE_TIMEOUT_SECONDS | tonumber),
          maxOutputTokens:(env.SMOKE_MAX_OUTPUT_TOKENS | tonumber),
          recommendationPrompt:env.SMOKE_RECOMMENDATION_PROMPT,
          preferencePrompt:env.SMOKE_PREFERENCE_PROMPT,
          softPreferenceEnabled:true,
          planCount:(env.SMOKE_PLAN_COUNT | tonumber),
          version:(env.SMOKE_CONFIG_VERSION | tonumber)
        }'
      ;;
    recommendation)
      jq -cn '{
        birthDate:"1992-01-20",
        wish:"希望得到适合日常佩戴、风格从容的材质搭配"
      }'
      ;;
    *)
      printf 'ERROR: unknown internal payload kind\n' >&2
      return 1
      ;;
  esac
}

safe_error_summary() {
  local operation="$1"
  local code='INVALID_JSON'
  local trace_id=''
  if jq -e 'type == "object"' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
    code="$(jq -r '(.code // "UNKNOWN") | tostring' <<< "$RESPONSE_BODY")"
    trace_id="$(jq -r '(.traceId // "") | tostring' <<< "$RESPONSE_BODY")"
  fi
  if [[ -n "$trace_id" ]]; then
    printf 'ERROR: %s failed (HTTP %s, code %s, traceId %s)\n' \
      "$operation" "$HTTP_STATUS" "$code" "$trace_id" >&2
  else
    printf 'ERROR: %s failed (HTTP %s, code %s)\n' \
      "$operation" "$HTTP_STATUS" "$code" >&2
  fi
}

call_api() {
  local operation="$1"
  local method="$2"
  local path="$3"
  local access_token="$4"
  local payload_kind="${5:-}"
  local response_file
  local -a curl_arguments

  response_counter=$((response_counter + 1))
  response_file="$temp_dir/response-$response_counter.json"
  : > "$response_file"
  chmod 600 "$response_file"

  curl_arguments=(
    --silent
    --show-error
    --connect-timeout 5
    --max-time "$((timeout_seconds + 15))"
    --output "$response_file"
    --write-out '%{http_code}'
    --request "$method"
  )
  if [[ -n "$access_token" ]]; then
    curl_arguments+=(--header "Authorization: Bearer $access_token")
  fi
  if [[ -n "$payload_kind" ]]; then
    curl_arguments+=(
      --header 'Content-Type: application/json'
      --data-binary @-
    )
  fi
  curl_arguments+=("$base_url$path")

  if [[ -n "$payload_kind" ]]; then
    if ! HTTP_STATUS="$(build_payload "$payload_kind" | curl "${curl_arguments[@]}")"; then
      printf 'ERROR: %s transport failed; no response body was logged\n' \
        "$operation" >&2
      exit 1
    fi
  elif ! HTTP_STATUS="$(curl "${curl_arguments[@]}")"; then
    printf 'ERROR: %s transport failed; no response body was logged\n' \
      "$operation" >&2
    exit 1
  fi

  RESPONSE_BODY="$(<"$response_file")"
  rm -f -- "$response_file"
  if ! jq -e . >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
    safe_error_summary "$operation"
    exit 1
  fi
}

expect_ok() {
  local operation="$1"
  if [[ ! "$HTTP_STATUS" =~ ^2[0-9][0-9]$ ]] \
      || ! jq -e '.code == "OK"' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
    safe_error_summary "$operation"
    exit 1
  fi
}

expect_error() {
  local operation="$1"
  local expected_code="$2"
  local alternate_code="${3:-}"
  if [[ "$HTTP_STATUS" != '503' ]] \
      || ! jq -e --arg code "$expected_code" --arg alternate "$alternate_code" \
        '(.code == $code or ($alternate != "" and .code == $alternate))
          and (.data == null)' \
        >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
    safe_error_summary "$operation"
    printf 'ERROR: expected HTTP 503 with code %s%s\n' \
      "$expected_code" \
      "${alternate_code:+ or $alternate_code}" >&2
    exit 1
  fi
}

installation_id="$(new_installation_id)"
call_api 'guest session' POST '/api/app/session/guest' '' guest
expect_ok 'guest session'
customer_token="$(jq -er \
  '.data.accessToken | select(type == "string" and length > 0)' \
  <<< "$RESPONSE_BODY")" || {
  printf 'ERROR: guest session did not return a customer access token\n' >&2
  exit 1
}

call_api \
  'recommendation status' \
  GET \
  '/api/public/recommendations/status' \
  ''
expect_ok 'recommendation status'
if ! jq -e '
  (.data.status == "AVAILABLE" and .data.available == true)
  or
  ((.data.status == "NOT_CONFIGURED" or .data.status == "DISABLED")
    and .data.available == false)
' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
  printf 'ERROR: recommendation status was not an exact supported state\n' >&2
  exit 1
fi
initial_status="$(jq -r '.data.status' <<< "$RESPONSE_BODY")"

if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
  if [[ "$initial_status" == 'AVAILABLE' ]]; then
    printf '%s\n' \
      'ERROR: backend recommendation status is AVAILABLE, so the default paused-path assertion is invalid.' \
      'Re-run with DEEPSEEK_API_KEY and AI_CONFIG_ENCRYPTION_KEY to opt in to the live DeepSeek path; no recommendation POST was sent.' >&2
    exit 1
  fi

  if [[ "$initial_status" == 'NOT_CONFIGURED' ]]; then
    expected_error='AI_NOT_CONFIGURED'
    alternate_error='AI_PROMPT_NOT_CONFIGURED'
  else
    expected_error='AI_DISABLED'
    alternate_error=''
  fi
  call_api \
    'paused recommendation POST' \
    POST \
    '/api/app/recommendations' \
    "$customer_token" \
    recommendation
  expect_error \
    'paused recommendation POST' \
    "$expected_error" \
    "$alternate_error"
  printf '%s\n' \
    "AI 推荐暂停路径 HTTP 冒烟通过：状态 $initial_status 与返回配置状态匹配；未调用 DeepSeek，也未使用本地降级。"
  exit 0
fi

if [[ -z "${AI_CONFIG_ENCRYPTION_KEY:-}" ]]; then
  printf '%s\n' \
    'ERROR: DEEPSEEK_API_KEY is set but AI_CONFIG_ENCRYPTION_KEY is empty.' \
    'Start the backend with a Base64-encoded 32-byte AI master key and rerun; no live DeepSeek request was made.' >&2
  exit 1
fi

call_api 'administrator login' POST '/api/admin/auth/login' '' admin-login
expect_ok 'administrator login'
admin_token="$(jq -er \
  '.data.accessToken | select(type == "string" and length > 0)' \
  <<< "$RESPONSE_BODY")" || {
  printf 'ERROR: administrator login did not return an access token\n' >&2
  exit 1
}

call_api 'read AI configuration' GET '/api/admin/ai/config' "$admin_token"
expect_ok 'read AI configuration'
config_version="$(jq -er \
  '.data.version | select(type == "number" and . >= 0 and floor == .)' \
  <<< "$RESPONSE_BODY")" || {
  printf 'ERROR: AI configuration did not return a valid version\n' >&2
  exit 1
}

call_api \
  'save AI configuration' \
  PUT \
  '/api/admin/ai/config' \
  "$admin_token" \
  save-config
if [[ ! "$HTTP_STATUS" =~ ^2[0-9][0-9]$ ]] \
    || ! jq -e '.code == "OK"' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
  safe_error_summary 'save AI configuration'
  printf '%s\n' \
    'ERROR: confirm the backend received the same valid Base64-encoded 32-byte AI_CONFIG_ENCRYPTION_KEY before startup.' >&2
  exit 1
fi
if ! jq -e \
  --argjson timeout "$timeout_seconds" \
  --argjson maxTokens "$max_output_tokens" \
  --argjson planCount "$plan_count" \
  --arg prompt "$recommendation_prompt" \
  --arg preferencePrompt "$preference_prompt" '
    .data.provider == "DEEPSEEK"
    and .data.baseUrl == "https://api.deepseek.com"
    and .data.modelName == "deepseek-v4-flash"
    and .data.apiKeyConfigured == true
    and .data.enabled == true
    and .data.timeoutSeconds == $timeout
    and .data.maxOutputTokens == $maxTokens
    and .data.recommendationPrompt == $prompt
    and .data.preferencePrompt == $preferencePrompt
    and .data.softPreferenceEnabled == true
    and .data.planCount == $planCount
  ' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
  printf 'ERROR: saved AI configuration did not match the requested safe contract\n' >&2
  exit 1
fi

call_api \
  'test DeepSeek configuration' \
  POST \
  '/api/admin/ai/config/test' \
  "$admin_token"
expect_ok 'test DeepSeek configuration'

call_api \
  'configured recommendation status' \
  GET \
  '/api/public/recommendations/status' \
  ''
expect_ok 'configured recommendation status'
if ! jq -e \
  '.data.status == "AVAILABLE" and .data.available == true' \
  >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
  printf 'ERROR: configured recommendation status is not AVAILABLE\n' >&2
  exit 1
fi

call_api \
  'live recommendation POST' \
  POST \
  '/api/app/recommendations' \
  "$customer_token" \
  recommendation
expect_ok 'live recommendation POST'
recommendation_response="$RESPONSE_BODY"

catalog_ids='[]'
catalog_page=1
catalog_fetched=0
while :; do
  call_api \
    "public catalog page $catalog_page" \
    GET \
    "/api/public/catalog/beads?page=$catalog_page&size=$catalog_page_size" \
    ''
  expect_ok "public catalog page $catalog_page"
  if ! jq -e '
    .data.items | type == "array"
  ' >/dev/null 2>&1 <<< "$RESPONSE_BODY"; then
    printf 'ERROR: public catalog page %d did not contain an items array\n' \
      "$catalog_page" >&2
    exit 1
  fi
  catalog_total="$(jq -er \
    '.data.total | select(type == "number" and . >= 0 and floor == .)' \
    <<< "$RESPONSE_BODY")" || {
    printf 'ERROR: public catalog page %d did not contain a valid total\n' \
      "$catalog_page" >&2
    exit 1
  }
  page_ids="$(jq -c '[.data.items[]?.id]' <<< "$RESPONSE_BODY")"
  page_count="$(jq 'length' <<< "$page_ids")"
  catalog_ids="$(jq -cn \
    --argjson current "$catalog_ids" \
    --argjson page "$page_ids" \
    '$current + $page | unique')"
  catalog_fetched=$((catalog_fetched + page_count))

  if (( catalog_fetched >= catalog_total )); then
    break
  fi
  if (( page_count == 0 )); then
    printf 'ERROR: public catalog pagination stopped before total records were fetched\n' >&2
    exit 1
  fi
  catalog_page=$((catalog_page + 1))
done

if ! validate_live_recommendation "$recommendation_response" "$catalog_ids"; then
  printf '%s\n' \
    'ERROR: live recommendation failed the configured-plan-count, birthday-profile, current-catalog, disclaimer, or image assertions.' >&2
  exit 1
fi

printf '%s\n' \
  "AI 推荐 live HTTP 冒烟通过：DeepSeek 连接测试与 $plan_count 套库存材质推荐成功，目录共检查 $catalog_page 页；未输出任何密钥或令牌。"
