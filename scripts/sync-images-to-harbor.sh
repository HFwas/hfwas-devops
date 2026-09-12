#!/usr/bin/env bash
# 从 deploy/ 下所有 images.txt 提取镜像，经本机代理拉取后推到 k3s Harbor。
#
# 用法:
#   ./scripts/sync-images-to-harbor.sh collect   # 只汇总清单到 deploy/images.txt
#   ./scripts/sync-images-to-harbor.sh pull      # 经代理拉取
#   ./scripts/sync-images-to-harbor.sh push      # 登录 Harbor 并推送
#   ./scripts/sync-images-to-harbor.sh all       # collect → pull → push（默认）
#   ./scripts/sync-images-to-harbor.sh list      # 打印去重后的镜像
#
# 环境变量:
#   HTTP_PROXY / HTTPS_PROXY   默认 http://127.0.0.1:7890
#   HARBOR_HOST                默认 localhost:30002
#   HARBOR_PROJECT             默认 library
#   HARBOR_USER                默认 admin
#   HARBOR_PASSWORD            缺省时从 k3s harbor-core Secret 读取
#   K3S_CONTAINER              默认 devops-k3s
#   SKIP_PULL_PREFIX           逗号分隔，这些前缀不走远程 pull（默认 hfwas/）
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

PROXY="${HTTP_PROXY:-${HTTPS_PROXY:-http://127.0.0.1:7890}}"
HARBOR_HOST="${HARBOR_HOST:-localhost:30002}"
HARBOR_PROJECT="${HARBOR_PROJECT:-library}"
HARBOR_USER="${HARBOR_USER:-admin}"
K3S_CONTAINER="${K3S_CONTAINER:-devops-k3s}"
SKIP_PULL_PREFIX="${SKIP_PULL_PREFIX:-hfwas/}"
IMAGES_OUT="$ROOT_DIR/deploy/images.txt"

usage() {
  sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
}

# 拉取走代理；推 Harbor / 访问本机必须绕过代理。
export_proxy() {
  export HTTP_PROXY="$PROXY"
  export HTTPS_PROXY="$PROXY"
  export ALL_PROXY="$PROXY"
  export http_proxy="$PROXY"
  export https_proxy="$PROXY"
  export NO_PROXY="localhost,127.0.0.1,${HARBOR_HOST},harbor.harbor.svc,harbor.harbor.svc.cluster.local"
  export no_proxy="$NO_PROXY"
}

image_files() {
  find "$ROOT_DIR/deploy" -type f \( -name 'images.txt' -o -name '*-images.txt' \) ! -path "$IMAGES_OUT" | sort
}

read_image_lines() {
  local file=$1
  grep -E -v '^[[:space:]]*(#|$)' "$file" || true
}

# 汇总 deploy/**/images.txt，去重后写 deploy/images.txt
collect_images() {
  local tmp
  tmp=$(mktemp)
  local f
  for f in $(image_files); do
    log "读取 $f"
    read_image_lines "$f" >>"$tmp"
  done
  if [ ! -s "$tmp" ]; then
    rm -f "$tmp"
    die "未在 deploy/ 下找到任何镜像"
  fi
  {
    echo "# 由 scripts/sync-images-to-harbor.sh collect 从 deploy/**/images.txt 汇总"
    echo "# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "#"
    sort -u "$tmp"
  } >"$IMAGES_OUT"
  rm -f "$tmp"
  local n
  n=$(grep -E -vc '^[[:space:]]*(#|$)' "$IMAGES_OUT")
  log "已写入 ${IMAGES_OUT} (${n} 个镜像)"
}

list_images() {
  if [ ! -f "$IMAGES_OUT" ]; then
    collect_images
  fi
  read_image_lines "$IMAGES_OUT"
}

# 去掉 digest；去掉 docker.io / 官方 library 前缀，得到 Harbor 仓库路径。
harbor_ref() {
  local src=$1
  local ref="${src%%@*}"
  local name tag path
  if [[ "$ref" == *":"* && "$ref" != *"://"* ]]; then
    tag="${ref##*:}"
    name="${ref%:*}"
    if [[ "$tag" == *"/"* ]]; then
      name="$ref"
      tag="latest"
    fi
  else
    name="$ref"
    tag="latest"
  fi
  path="$name"
  case "$path" in
    docker.io/*) path="${path#docker.io/}" ;;
    index.docker.io/*) path="${path#index.docker.io/}" ;;
    quay.io/*) path="${path#quay.io/}" ;;
    ghcr.io/*) path="${path#ghcr.io/}" ;;
    gcr.io/*) path="${path#gcr.io/}" ;;
    registry.k8s.io/*) path="${path#registry.k8s.io/}" ;;
  esac
  if [[ "$path" == library/* ]]; then
    path="${path#library/}"
  fi
  printf '%s/%s/%s:%s\n' "$HARBOR_HOST" "$HARBOR_PROJECT" "$path" "$tag"
}

should_skip_pull() {
  local image=$1
  local p
  IFS=',' read -ra parts <<<"$SKIP_PULL_PREFIX"
  for p in "${parts[@]}"; do
    [ -n "$p" ] || continue
    case "$image" in
      "$p"*) return 0 ;;
    esac
  done
  return 1
}

image_exists_locally() {
  docker image inspect "$1" >/dev/null 2>&1
}

check_proxy() {
  if ! curl -fsS -o /dev/null -x "$PROXY" --connect-timeout 3 https://www.google.com >/dev/null 2>&1 \
    && ! curl -fsS -o /dev/null -x "$PROXY" --connect-timeout 3 https://registry-1.docker.io/v2/ >/dev/null 2>&1; then
    log "WARN: 代理 $PROXY 探测失败，仍继续拉取（请确认 Clash 已开系统代理/允许局域网）"
  else
    log "代理可用: $PROXY"
  fi
}

pull_images() {
  require_docker
  require_cmd curl
  export_proxy
  log "使用代理拉取: $PROXY"
  check_proxy

  local image ok=0 fail=0 skip=0
  while IFS= read -r image; do
    [ -n "$image" ] || continue
    if should_skip_pull "$image"; then
      if image_exists_locally "$image"; then
        log "跳过远程拉取（本地已有）: $image"
      else
        log "WARN: 跳过远程拉取（需本地构建）: $image"
      fi
      skip=$((skip + 1))
      continue
    fi
    log "pull $image"
    if docker pull "$image"; then
      ok=$((ok + 1))
    else
      log "WARN: 拉取失败: $image"
      fail=$((fail + 1))
    fi
  done < <(list_images)

  log "拉取结束: 成功 ${ok}，跳过 ${skip}，失败 ${fail}"
  [ "$fail" -eq 0 ]
}

harbor_password() {
  if [ -n "${HARBOR_PASSWORD:-}" ]; then
    printf '%s' "$HARBOR_PASSWORD"
    return 0
  fi
  local b64=""
  local secret
  for secret in harbor-core harbor-harbor-core; do
    b64=$(docker exec "$K3S_CONTAINER" kubectl -n harbor get secret "$secret" \
      -o jsonpath='{.data.HARBOR_ADMIN_PASSWORD}' 2>/dev/null || true)
    if [ -n "$b64" ]; then
      printf '%s' "$b64" | base64 -d
      return 0
    fi
  done
  die "无法读取 Harbor 密码。设置 HARBOR_PASSWORD，或确认 $K3S_CONTAINER 中 harbor 已安装"
}

ensure_harbor_project() {
  local pwd=$1
  local url="http://${HARBOR_HOST}/api/v2.0/projects?project_name=${HARBOR_PROJECT}"
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' -u "${HARBOR_USER}:${pwd}" "$url" || true)
  if [ "$code" = "200" ]; then
    log "Harbor 项目已存在: $HARBOR_PROJECT"
    return 0
  fi
  log "创建 Harbor 项目: $HARBOR_PROJECT"
  curl -sS -f -u "${HARBOR_USER}:${pwd}" \
    -H 'Content-Type: application/json' \
    -X POST "http://${HARBOR_HOST}/api/v2.0/projects" \
    -d "{\"project_name\":\"${HARBOR_PROJECT}\",\"public\":true}" >/dev/null
}

login_harbor() {
  local pwd=$1
  require_cmd curl
  if ! curl -fsS -o /dev/null --connect-timeout 5 "http://${HARBOR_HOST}/api/v2.0/health"; then
    die "Harbor 不可达: http://${HARBOR_HOST}（确认 k3s NodePort 30002 与 insecure-registries）"
  fi
  ensure_harbor_project "$pwd"
  log "docker login ${HARBOR_HOST}"
  if ! printf '%s' "$pwd" | docker login "$HARBOR_HOST" -u "$HARBOR_USER" --password-stdin; then
    die "docker login 失败。请把 ${HARBOR_HOST} 加入 Docker insecure-registries 后重启 Docker/Colima"
  fi
}

push_images() {
  require_docker
  export_proxy
  # 推送本机 Harbor 不走代理
  unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy || true

  local pwd
  pwd=$(harbor_password)
  login_harbor "$pwd"

  local image dest ok=0 fail=0
  while IFS= read -r image; do
    [ -n "$image" ] || continue
    dest=$(harbor_ref "$image")
    if ! image_exists_locally "$image" && ! image_exists_locally "${image%%@*}"; then
      log "WARN: 本地没有镜像，跳过推送: $image"
      fail=$((fail + 1))
      continue
    fi
    log "tag  $image -> $dest"
    if ! docker tag "$image" "$dest" 2>/dev/null; then
      docker tag "${image%%@*}" "$dest"
    fi
    log "push $dest"
    if docker push "$dest"; then
      ok=$((ok + 1))
    else
      log "WARN: 推送失败: $dest"
      fail=$((fail + 1))
    fi
  done < <(list_images)

  log "推送结束: 成功 ${ok}，失败 ${fail}"
  log "Harbor UI: http://${HARBOR_HOST}  项目 ${HARBOR_PROJECT}"
  [ "$fail" -eq 0 ]
}

cmd="${1:-all}"
case "$cmd" in
  -h|--help|help) usage ;;
  collect) collect_images ;;
  list) list_images ;;
  pull)
    [ -f "$IMAGES_OUT" ] || collect_images
    pull_images
    ;;
  push)
    [ -f "$IMAGES_OUT" ] || collect_images
    push_images
    ;;
  all)
    collect_images
    pull_images
    push_images
    ;;
  *)
    usage
    die "未知命令: $cmd"
    ;;
esac
