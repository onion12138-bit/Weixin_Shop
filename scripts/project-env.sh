#!/usr/bin/env bash

# 本脚本必须通过 source 执行，因为环境变量需要作用于当前终端。
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "请执行：source ./scripts/project-env.sh" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tool_root="$(cd "${project_root}/../.." && pwd)"

export JAVA_HOME
JAVA_HOME="$(/usr/libexec/java_home -v 17)"

export MAVEN_HOME="${tool_root}/apache-maven-3.9.16"
export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:/Applications/Docker.app/Contents/Resources/bin:${PATH}"

echo "项目环境已启用：Java 17、Maven 3.9.16、Docker Desktop CLI"
