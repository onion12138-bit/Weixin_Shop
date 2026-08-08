#!/bin/zsh

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH}"
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
PUBLISH_BRANCH="codex/publish-excel-import"

pause_and_exit() {
  local exit_code="$1"
  echo
  if [[ "${exit_code}" -eq 0 ]]; then
    echo "操作完成，可以关闭此窗口。"
  else
    echo "操作未完成，请保留窗口中的错误信息并发给 Codex。"
  fi
  read -r "?按回车键关闭窗口..."
  exit "${exit_code}"
}

fail() {
  echo "错误：$1"
  pause_and_exit 1
}

cd "${PROJECT_ROOT}" || fail "无法进入项目目录"

if ! git show-ref --verify --quiet "refs/heads/${PUBLISH_BRANCH}"; then
  fail "找不到已准备好的发布分支 ${PUBLISH_BRANCH}"
fi

if ! command -v gh >/dev/null 2>&1; then
  if ! command -v brew >/dev/null 2>&1; then
    fail "未安装 Homebrew，无法自动安装 GitHub CLI"
  fi
  echo "正在安装 GitHub 官方命令行工具..."
  brew install gh || fail "GitHub CLI 安装失败"
fi

if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "即将打开浏览器，请登录并授权 GitHub。"
  gh auth login --hostname github.com --git-protocol https --web \
    || fail "GitHub 登录未完成"
fi

gh auth setup-git || fail "Git 凭据配置失败"

echo "正在确认远程 main 没有新增提交..."
git fetch origin main || fail "无法获取远程 main"
if ! git merge-base --is-ancestor origin/main "${PUBLISH_BRANCH}"; then
  fail "远程 main 已发生变化，为避免覆盖他人提交，已停止推送"
fi

echo "正在推送 Excel 批量导入和图片拖拽上传功能..."
git push origin "${PUBLISH_BRANCH}:main" || fail "推送失败"

REMOTE_SHA="$(git ls-remote origin refs/heads/main | awk '{print $1}')"
LOCAL_SHA="$(git rev-parse "${PUBLISH_BRANCH}")"
if [[ "${REMOTE_SHA}" != "${LOCAL_SHA}" ]]; then
  fail "远程提交校验不一致"
fi

git branch -f main "${PUBLISH_BRANCH}" \
  || fail "远程已推送，但本地 main 指针更新失败"

echo "GitHub main 推送成功：${LOCAL_SHA}"
pause_and_exit 0
