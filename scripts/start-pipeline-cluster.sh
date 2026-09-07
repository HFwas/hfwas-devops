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

  停止: docker compose --profile pipeline stop k3s
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

mkdir -p "$ROOT_DIR/data/pipeline"
log "启动 k3s + 安装 Tekton（compose profile=pipeline）..."
compose --profile pipeline up -d k3s
compose --profile pipeline up --abort-on-container-exit tekton-install
log "集群已就绪。kubeconfig: $ROOT_DIR/data/pipeline/kubeconfig.yaml"
log "重启后端后即可真正执行流水线。"
