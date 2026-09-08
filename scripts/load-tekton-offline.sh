#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

OUT_DIR="${TEKTON_OFFLINE_DIR:-$ROOT_DIR/data/tekton-offline}"
K3S_CONTAINER="${K3S_CONTAINER:-devops-k3s}"

usage() {
  cat <<EOF
用法: $(basename "$0") [tar...]

  把离线镜像 tar 导入当前节点的 containerd / 本仓库 k3s 容器。
  未传路径时导入 $OUT_DIR/*.tar
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

tars=("$@")
if [ "${#tars[@]}" -eq 0 ]; then
  shopt -s nullglob
  tars=("$OUT_DIR"/*.tar)
  shopt -u nullglob
fi
if [ "${#tars[@]}" -eq 0 ]; then
  log "没有找到离线 tar（$OUT_DIR）。在线安装将尝试拉取 ghcr.io。"
  exit 0
fi

import_k3s() {
  local host_path=$1
  local name
  name="$(basename "$host_path")"
  docker cp "$host_path" "$K3S_CONTAINER:/tmp/$name"
  # 容器内 `k3s ctr` 不可用（symlink 多路调用）；k3s 的 kubelet 读 k8s.io namespace。
  docker exec "$K3S_CONTAINER" ctr -n k8s.io images import "/tmp/$name"
}

import_host() {
  local host_path=$1
  if command -v k3s >/dev/null 2>&1; then
    sudo k3s ctr images import "$host_path"
  elif command -v nerdctl >/dev/null 2>&1; then
    sudo nerdctl --namespace k8s.io load -i "$host_path"
  elif command -v ctr >/dev/null 2>&1; then
    sudo ctr -n k8s.io images import "$host_path"
  else
    die "未找到 k3s/nerdctl/ctr，且没有运行中的 $K3S_CONTAINER"
  fi
}

for tar in "${tars[@]}"; do
  [ -f "$tar" ] || die "不是文件: $tar"
  log "导入 $tar"
  if docker inspect "$K3S_CONTAINER" >/dev/null 2>&1; then
    import_k3s "$tar"
  else
    import_host "$tar"
  fi
done
log "镜像已导入。"
