#!/usr/bin/env bash
# 把当前分支已提交代码推到本机 GitLab
#
# 用法:
#   ./scripts/sync-gitlab.sh              # 推当前分支
#   ./scripts/sync-gitlab.sh status       # 只看本地 vs GitLab，不推
#   ./scripts/sync-gitlab.sh -b dev       # 指定分支
#
# 环境变量:
#   GITLAB_URL       默认 http://localhost:30880
#   GITLAB_PROJECT   默认 root/hfwas-devops
#   GITLAB_USER      默认 root
#   GITLAB_PASSWORD  默认读 deploy/charts/gitlab/values.yaml 的 rootPassword
#   GITLAB_TOKEN     若设置则优先于密码（推荐 PAT）
#   GITLAB_REMOTE    默认 gitlab
#
# 只推已提交内容。工作区未提交改动不会上去。
# 网页请打开 /-/commits/<分支>，不要打开某个 commit SHA。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALUES_FILE="$ROOT_DIR/deploy/charts/gitlab/values.yaml"
COMMAND="push"
BRANCH=""
REMOTE="${GITLAB_REMOTE:-gitlab}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

usage() {
  sed -n '2,18p' "$0" | sed 's/^# \?//'
}

while [ $# -gt 0 ]; do
  case "$1" in
    push|status) COMMAND="$1" ;;
    -b|--branch) BRANCH="${2:-}"; shift ;;
    -r|--remote) REMOTE="${2:-}"; shift ;;
    -h|--help|help) usage; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
  shift || true
done

# 本机 GitLab 不要走 Clash 代理
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy || true
export NO_PROXY='*' no_proxy='*'

GITLAB_URL="${GITLAB_URL:-http://localhost:30880}"
GITLAB_URL="${GITLAB_URL%/}"
GITLAB_PROJECT="${GITLAB_PROJECT:-root/hfwas-devops}"
GITLAB_PROJECT="${GITLAB_PROJECT#/}"

if [ -n "${GITLAB_TOKEN:-}" ]; then
  GITLAB_USER="oauth2"
  GITLAB_SECRET="$GITLAB_TOKEN"
else
  GITLAB_USER="${GITLAB_USER:-root}"
  if [ -n "${GITLAB_PASSWORD:-}" ]; then
    GITLAB_SECRET="$GITLAB_PASSWORD"
  else
    [ -f "$VALUES_FILE" ] || die "找不到 $VALUES_FILE，可设 GITLAB_PASSWORD 或 GITLAB_TOKEN"
    GITLAB_SECRET="$(sed -n 's/^rootPassword: *"\(.*\)"/\1/p' "$VALUES_FILE" | head -n1)"
    [ -n "$GITLAB_SECRET" ] || die "values.yaml 里没有 rootPassword"
  fi
fi

PUSH_URL="${GITLAB_URL}/${GITLAB_PROJECT}.git"

gitlab_git() {
  local ask
  ask="$ROOT_DIR/scripts/gitlab-askpass.sh"
  [ -x "$ask" ] || chmod +x "$ask"
  GIT_ASKPASS="$ask" \
    GIT_TERMINAL_PROMPT=0 \
    GCM_INTERACTIVE=never \
    HFWAS_GITLAB_USER="$GITLAB_USER" \
    HFWAS_GITLAB_SECRET="$GITLAB_SECRET" \
    git -c credential.helper= -c "core.askPass=$ask" "$@"
}

current_branch() {
  local b
  b="$(git rev-parse --abbrev-ref HEAD)"
  [ -n "$b" ] && [ "$b" != "HEAD" ] || die "当前不在命名分支上，请先 checkout 一个分支。"
  printf '%s' "$b"
}

dirty_warning() {
  if [ -n "$(git status --porcelain)" ]; then
    echo
    log "工作区有未提交改动，本次不会推上去："
    git status --short
    echo
  fi
}

probe_gitlab() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 8 "${GITLAB_URL}/users/sign_in" || true)"
  [ "$code" = "200" ] || die "GitLab 连不上 ${GITLAB_URL} (HTTP ${code:-fail})。确认 Pod 已就绪。"
}

ensure_remote() {
  if git remote | grep -qx "$REMOTE"; then
    local current
    current="$(git remote get-url "$REMOTE")"
    if [ "$current" != "$PUSH_URL" ]; then
      log "远程 $REMOTE 当前是 $current"
      log "本次仍推到该地址。要改成 $PUSH_URL 可执行: git remote set-url $REMOTE $PUSH_URL"
    fi
  else
    git remote add "$REMOTE" "$PUSH_URL"
    log "已添加远程 $REMOTE -> $PUSH_URL"
  fi
}

ensure_project() {
  local encoded api code
  encoded="$(printf '%s' "$GITLAB_PROJECT" | sed 's|/|%2F|g')"
  api="${GITLAB_URL}/api/v4/projects/${encoded}"
  code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 -u "${GITLAB_USER}:${GITLAB_SECRET}" "$api" || true)"
  if [ "$code" = "200" ]; then
    log "项目已存在: $GITLAB_PROJECT"
    return
  fi
  if [ "$code" != "404" ]; then
    log "查询项目失败 HTTP ${code:-fail}，继续尝试 git push。"
    return
  fi
  local name
  name="${GITLAB_PROJECT##*/}"
  code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 -u "${GITLAB_USER}:${GITLAB_SECRET}" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"${name}\",\"path\":\"${name}\",\"visibility\":\"private\",\"initialize_with_readme\":false}" \
    "${GITLAB_URL}/api/v4/projects" || true)"
  if [ "$code" = "201" ] || [ "$code" = "200" ]; then
    log "已创建项目: $GITLAB_PROJECT"
  else
    log "自动建库失败 HTTP ${code:-fail}。若 push 报 repository not found，请先在网页建空项目。"
  fi
}

show_status() {
  local head remote_sha
  head="$(git rev-parse --short HEAD)"
  log "GitLab: ${GITLAB_URL}/${GITLAB_PROJECT}"
  log "本地分支: ${BRANCH} (${head})"
  if remote_sha="$(git rev-parse --verify --short "refs/remotes/${REMOTE}/${BRANCH}" 2>/dev/null)"; then
    log "GitLab ${BRANCH} : ${remote_sha}"
    if [ "$remote_sha" = "$head" ]; then
      log "已与 GitLab 对齐。"
    else
      log "不一致。运行 ./scripts/sync-gitlab.sh 推送。"
    fi
  else
    log "本地还没有 ${REMOTE}/${BRANCH} 跟踪分支。"
  fi
  log "提交页: ${GITLAB_URL}/${GITLAB_PROJECT}/-/commits/${BRANCH}"
  dirty_warning
}

cd "$ROOT_DIR"
command -v git >/dev/null 2>&1 || die "未找到 git"
command -v curl >/dev/null 2>&1 || die "未找到 curl"

[ "$COMMAND" = "push" ] || [ "$COMMAND" = "status" ] || die "未知命令: $COMMAND 。用 push 或 status。"
[ -n "$BRANCH" ] || BRANCH="$(current_branch)"

probe_gitlab
ensure_remote

if [ "$COMMAND" = "status" ]; then
  gitlab_git fetch "$REMOTE" --prune
  show_status
  exit 0
fi

ensure_project
dirty_warning
HEAD_SHORT="$(git rev-parse --short HEAD)"
log "推送 ${HEAD_SHORT} -> ${REMOTE}/${BRANCH}"
gitlab_git push "$REMOTE" "HEAD:${BRANCH}"
gitlab_git fetch "$REMOTE" "$BRANCH"

echo
log "完成: ${HEAD_SHORT} -> ${REMOTE}/${BRANCH}"
log "提交页: ${GITLAB_URL}/${GITLAB_PROJECT}/-/commits/${BRANCH}"
log "代码树: ${GITLAB_URL}/${GITLAB_PROJECT}/-/tree/${BRANCH}"
dirty_warning
