#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

usage() {
  cat <<EOF
用法: $(basename "$0")

  启动本地流水线执行集群：k3s + Tekton Pipelines。
  kubeconfig 写出到 data/pipeline/kubeconfig.yaml，
  随后 scripts/start-backend.sh 会自动把它传给 pipeline.kubeconfig。

  离线：先在有网机器执行 scripts/pack-tekton-offline.sh，
  把 data/tekton-offline/*.tar 放到本机同目录后再跑本脚本。

  停止: docker compose --profile pipeline stop k3s
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

mkdir -p "$ROOT_DIR/data/pipeline" "$ROOT_DIR/data/tekton-offline"
log "启动 k3s + 安装 Tekton（compose profile=pipeline）..."
compose --profile pipeline up -d k3s
# wait until healthy so ctr import can talk to containerd
i=0
until docker exec devops-k3s k3s kubectl get --raw=/readyz >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    die "k3s 未就绪"
  fi
  sleep 2
done
if ls "$ROOT_DIR/data/tekton-offline"/*.tar >/dev/null 2>&1; then
  "$SCRIPT_DIR/load-tekton-offline.sh"
else
  log "未找到 data/tekton-offline/*.tar，将在线拉取 Tekton 镜像（离线请先 pack-tekton-offline.sh）"
fi
compose --profile pipeline up --abort-on-container-exit tekton-install
log "集群已就绪。kubeconfig: $ROOT_DIR/data/pipeline/kubeconfig.yaml"
log "重启后端后即可真正执行流水线。"
