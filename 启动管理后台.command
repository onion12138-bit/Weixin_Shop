#!/usr/bin/env bash
set -u

project_root="$(cd "$(dirname "$0")" && pwd)"
exec "${project_root}/scripts/start-local.sh"
