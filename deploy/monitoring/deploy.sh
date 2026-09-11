#!/usr/bin/env bash
# ============================================================
# Prometheus 监控栈离线部署脚本（k3s 集群）
# ============================================================
# 用法:
#   ./deploy.sh pull     # 拉取镜像到宿主机 Docker
#   ./deploy.sh images   # 导入镜像到 k3s containerd
#   ./deploy.sh deploy   # Helm 安装 kube-prometheus-stack
#   ./deploy.sh all      # 执行全部步骤（pull → images → deploy）
# ============================================================
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART="$ROOT_DIR/kube-prometheus-stack-90.0.0.tgz"
VALUES="$ROOT_DIR/prometheus-values.yaml"
IMAGES_FILE="$ROOT_DIR/images.txt"
K3S_CONTAINER="${K3S_CONTAINER:-devops-k3s}"
NAMESPACE="${NAMESPACE:-monitoring}"
RELEASE_NAME="${RELEASE_NAME:-prometheus}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

pull_images() {
  log "=== 拉取镜像到宿主机 Docker ==="
  require_cmd docker
  while IFS= read -r line; do
    [[ "$line" =~ ^#.*$ || -z "${line// /}" ]] && continue
    log "拉取: $line"
    docker pull "$line" || log "WARN: $line 拉取失败，跳过"
  done < "$IMAGES_FILE"
  log "镜像拉取完成"
}

import_images() {
  log "=== 导入镜像到 k3s containerd ==="
  require_cmd docker
  while IFS= read -r line; do
    [[ "$line" =~ ^#.*$ || -z "${line// /}" ]] && continue
    log "导入: $line"
    docker save "$line" | docker exec -i "$K3S_CONTAINER" ctr -n k8s.io images import - || log "WARN: $line 导入失败"
  done < "$IMAGES_FILE"

  log "验证导入结果:"
  docker exec "$K3S_CONTAINER" ctr -n k8s.io images ls | grep -E "prometheus|node-exporter|kube-state|k8s-sidecar|alertmanager"
}

deploy() {
  log "=== 部署 kube-prometheus-stack ==="
  require_cmd docker

  # 检查 chart 文件
  [ -f "$CHART" ] || die "Chart 文件不存在: $CHART"
  [ -f "$VALUES" ] || die "Values 文件不存在: $VALUES"

  # 创建命名空间
  docker exec "$K3S_CONTAINER" sh -c "
    kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml create namespace $NAMESPACE 2>/dev/null || true
  "

  # 复制 chart 和 values 到 k3s 容器
  docker cp "$CHART" "$K3S_CONTAINER:/tmp/$(basename "$CHART")"
  docker cp "$VALUES" "$K3S_CONTAINER:/tmp/$(basename "$VALUES")"

  # Helm 安装
  docker exec "$K3S_CONTAINER" sh -c "
    helm install $RELEASE_NAME /tmp/$(basename "$CHART") \
      -n $NAMESPACE \
      -f /tmp/$(basename "$VALUES") \
      --kubeconfig /etc/rancher/k3s/k3s.yaml \
      --timeout 10m \
      2>&1
  "

  log "安装完成，等待 Pod 启动..."
  sleep 20

  docker exec "$K3S_CONTAINER" sh -c "
    kubectl -n $NAMESPACE get pods --kubeconfig /etc/rancher/k3s/k3s.yaml 2>&1
  "
  log "部署完成！Prometheus API 地址: http://localhost:30090"
}

uninstall() {
  log "=== 卸载 $RELEASE_NAME ==="
  docker exec "$K3S_CONTAINER" sh -c "
    helm uninstall $RELEASE_NAME -n $NAMESPACE --kubeconfig /etc/rancher/k3s/k3s.yaml 2>/dev/null || true
    kubectl delete namespace $NAMESPACE --kubeconfig /etc/rancher/k3s/k3s.yaml 2>/dev/null || true
  "
  log "已卸载"
}

# ── 命令分发 ──
case "${1:-help}" in
  pull)    pull_images ;;
  images)  import_images ;;
  deploy)  deploy ;;
  all)     pull_images && import_images && deploy ;;
  uninstall) uninstall ;;
  help|*)
    echo "用法: $0 {pull|images|deploy|all|uninstall}"
    echo ""
    echo "  pull      拉取镜像到宿主机 Docker"
    echo "  images    导入镜像到 k3s containerd"
    echo "  deploy    Helm 安装 kube-prometheus-stack"
    echo "  all       执行全部步骤（pull → images → deploy）"
    echo "  uninstall 卸载 Prometheus"
    ;;
esac