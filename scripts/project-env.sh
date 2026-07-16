#!/usr/bin/env bash

# 本脚本必须通过 source 执行，因为环境变量需要作用于当前终端。
if [[ -n "${ZSH_VERSION:-}" ]]; then
  if [[ "${ZSH_EVAL_CONTEXT:-}" != *:file ]]; then
    echo "请执行：source ./scripts/project-env.sh" >&2
    exit 1
  fi
  script_path="${(%):-%N}"
else
  if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "请执行：source ./scripts/project-env.sh" >&2
    exit 1
  fi
  script_path="${BASH_SOURCE[0]}"
fi

project_root="$(cd "$(dirname "${script_path}")/.." && pwd)"
git_common_dir="$(git -C "${project_root}" rev-parse --path-format=absolute --git-common-dir)"
tool_root="$(dirname "${git_common_dir}")"

export JAVA_HOME
JAVA_HOME="$(/usr/libexec/java_home -v 17)"

export MAVEN_HOME="${tool_root}/apache-maven-3.9.16"
# Finder 双击 `.command` 时不会加载用户的交互式 Shell 配置，因此需要显式加入
# Apple Silicon 与 Intel Homebrew 的可执行目录，确保 Node.js 能被一键启动脚本找到。
export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:/Applications/Docker.app/Contents/Resources/bin:/opt/homebrew/bin:/usr/local/bin:${PATH}"

# `.env` 同时供 Docker Compose 和本地 Java/Node 进程使用。通过 set -a
# 自动导出其中变量，避免用户修改了密码或端口但后端仍读取默认值。
if [[ -f "${project_root}/.env" ]]; then
  set -a
  source "${project_root}/.env"
  set +a
fi

# 本地一键启动需要稳定的 AES-256 主密钥来加密 DeepSeek API Key。
# 未显式配置时只生成一次并以 0600 权限持久化；后续启动必须复用同一值，
# 否则数据库中已经保存的密文将无法解密。
local_secrets_dir="${PROJECT_LOCAL_SECRETS_DIR:-${project_root}/.local-secrets}"
ai_encryption_key_file="${local_secrets_dir}/ai-config-encryption-key"

valid_ai_encryption_key() {
  local decoded_size
  decoded_size="$(
    printf '%s' "${AI_CONFIG_ENCRYPTION_KEY}" \
      | openssl base64 -d -A 2>/dev/null \
      | wc -c \
      | tr -d '[:space:]'
  )" || return 1
  [[ "${decoded_size}" == "32" ]]
}

if [[ -z "${AI_CONFIG_ENCRYPTION_KEY:-}" ]]; then
  if ! command -v openssl >/dev/null 2>&1; then
    echo "缺少 openssl，无法生成 AI 本地加密密钥" >&2
    return 1
  fi
  mkdir -p "${local_secrets_dir}" || return 1
  chmod 700 "${local_secrets_dir}" || return 1
  if [[ ! -f "${ai_encryption_key_file}" ]]; then
    temporary_key_file="$(mktemp "${local_secrets_dir}/ai-key.XXXXXX")" \
      || return 1
    if ! openssl rand -base64 32 >"${temporary_key_file}"; then
      rm -f "${temporary_key_file}"
      return 1
    fi
    chmod 600 "${temporary_key_file}" || return 1
    # hard link 的目标创建是原子的：并发双击时只有一个进程能成为主密钥写入者，
    # 其他进程删除自己的临时文件并读取获胜文件，避免同一轮启动使用不同密钥。
    if ! ln "${temporary_key_file}" "${ai_encryption_key_file}" 2>/dev/null; then
      rm -f "${temporary_key_file}"
      [[ -f "${ai_encryption_key_file}" ]] || return 1
    else
      rm -f "${temporary_key_file}"
    fi
  fi
  IFS= read -r AI_CONFIG_ENCRYPTION_KEY <"${ai_encryption_key_file}" \
    || return 1
  export AI_CONFIG_ENCRYPTION_KEY
fi

if ! valid_ai_encryption_key; then
  echo "AI_CONFIG_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥" >&2
  return 1
fi

echo "项目环境已启用：Java 17、Maven 3.9.16、Docker Desktop CLI"
