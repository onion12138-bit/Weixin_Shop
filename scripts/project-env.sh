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

echo "项目环境已启用：Java 17、Maven 3.9.16、Docker Desktop CLI"
