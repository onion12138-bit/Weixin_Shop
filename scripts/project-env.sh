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
export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:/Applications/Docker.app/Contents/Resources/bin:${PATH}"

echo "项目环境已启用：Java 17、Maven 3.9.16、Docker Desktop CLI"
