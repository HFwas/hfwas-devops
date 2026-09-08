#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

TEKTON_DIR="$ROOT_DIR/deploy/tekton"
OUT_DIR="${TEKTON_OFFLINE_DIR:-$ROOT_DIR/data/tekton-offline}"
WITH_JOBS=0
WITH_TOOLCHAIN=0

usage() {
  cat <<EOF
用法: $(basename "$0") [--with-jobs] [--with-toolchain]

  在有网机器上拉取 Tekton / 任务镜像，保存为 tar，供离线集群导入。
  输出目录: $OUT_DIR
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --with-jobs) WITH_JOBS=1 ;;
    --with-toolchain) WITH_TOOLCHAIN=1 ;;
    *) die "未知参数: $1" ;;
  esac
  shift
done

require_cmd docker
mkdir -p "$OUT_DIR"

read_images() {
  local file=$1
  grep -vE '^[[:space:]]*(#|$)' "$file"
}

pull_and_save() {
  local list=$1
  local dest=$2
  local images=()
  while IFS= read -r image; do
    [ -n "$image" ] || continue
    log "pull $image"
    docker pull "$image"
    images+=("$image")
  done < <(read_images "$list")
  if [ "${#images[@]}" -eq 0 ]; then
    die "镜像列表为空: $list"
  fi
  log "save ${#images[@]} images -> $dest"
  docker save -o "$dest" "${images[@]}"
}

if [ -f "$ROOT_DIR/deploy/k3s/images.txt" ]; then
  pull_and_save "$ROOT_DIR/deploy/k3s/images.txt" "$OUT_DIR/k3s-system-images.tar"
fi
pull_and_save "$TEKTON_DIR/pipeline/v1.15.1/images.txt" "$OUT_DIR/pipeline-images.tar"
if [ "$WITH_JOBS" -eq 1 ]; then
  pull_and_save "$TEKTON_DIR/jobs/images.txt" "$OUT_DIR/job-images.tar"
fi
if [ "$WITH_TOOLCHAIN" -eq 1 ]; then
  pull_and_save "$TEKTON_DIR/jobs/toolchain-images.txt" "$OUT_DIR/toolchain-images.tar"
fi
log "完成。把 $OUT_DIR/*.tar 与 deploy/tekton 拷到离线环境。"
