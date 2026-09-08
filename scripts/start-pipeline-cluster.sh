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

  无离线 tar 时会在宿主机 docker pull，再 import 进 k3s
  （k3s 内嵌 containerd 往往访问不了 docker.io / ghcr.io）。

  离线：先在有网机器执行 scripts/pack-tekton-offline.sh，
  把 data/tekton-offline/*.tar 放到本机同目录后再跑本脚本。

  停止: docker compose --profile pipeline stop k3s
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

require_docker

# rancher/k3s 里 /bin/kubectl、/bin/ctr 都是 k3s 的 symlink，不能再写成 `k3s kubectl` / `k3s ctr`。
k3s_kubectl() {
  docker exec devops-k3s kubectl "$@"
}

read_image_list() {
  grep -vE '^[[:space:]]*(#|$)' "$1"
}

# k3s 内嵌 containerd 经常访问不了 docker.io/ghcr.io（DNS 污染或超时）；
# 宿主机 docker 能 pull 时，save 再 ctr import 进 k8s.io namespace。
# digest 拉取的镜像 RepoTags 为空，必须先 tag 成 name:tag 再 save；kubelet 若带 @sha256 还要 ctr tag 钉住。
import_images_from_host() {
  local list_file=$1
  local save_refs=()
  local pin_refs=()
  local image ref_tag
  while IFS= read -r image; do
    [ -n "$image" ] || continue
    log "host pull $image"
    docker pull "$image"
    ref_tag="${image%%@*}"
    docker tag "$image" "$ref_tag"
    save_refs+=("$ref_tag")
    pin_refs+=("$image")
  done < <(read_image_list "$list_file")
  if [ "${#save_refs[@]}" -eq 0 ]; then
    die "镜像列表为空: $list_file"
  fi
  local image_tar="$ROOT_DIR/data/tekton-offline/.host-import.tar"
  log "导入 ${#save_refs[@]} 张镜像到 k3s containerd..."
  docker save -o "$image_tar" "${save_refs[@]}"
  ls -lh "$image_tar"
  docker cp "$image_tar" devops-k3s:/tmp/host-import.tar
  docker exec devops-k3s ctr -n k8s.io images import /tmp/host-import.tar
  docker exec devops-k3s rm -f /tmp/host-import.tar
  rm -f "$image_tar"
  local i
  for i in "${!pin_refs[@]}"; do
    image=${pin_refs[$i]}
    ref_tag=${save_refs[$i]}
    if [ "$image" != "$ref_tag" ]; then
      docker exec devops-k3s ctr -n k8s.io images tag "$ref_tag" "$image" >/dev/null
    fi
  done
}

ensure_k3s_system_images() {
  local image_tar="$ROOT_DIR/data/tekton-offline/k3s-system-images.tar"
  local list="$ROOT_DIR/deploy/k3s/images.txt"
  if [ -f "$image_tar" ]; then
    log "导入 k3s 系统镜像: $image_tar"
    "$SCRIPT_DIR/load-tekton-offline.sh" "$image_tar"
  elif [ -f "$list" ]; then
    log "未找到 k3s-system-images.tar，从宿主机拉取 pause/coredns 等系统镜像"
    import_images_from_host "$list"
  else
    log "WARN: 没有 $list，系统 Pod 可能 ImagePullBackOff"
    return 0
  fi
  # 让 kubelet 用已导入的 pause 重建 sandbox
  k3s_kubectl delete pods -n kube-system --all --wait=false >/dev/null 2>&1 || true
}

write_compose_kubeconfig() {
  local src="$ROOT_DIR/data/pipeline/kubeconfig.yaml"
  local dst="$ROOT_DIR/data/pipeline/kubeconfig.compose.yaml"
  if [ ! -f "$src" ]; then
    die "k3s 未写出 kubeconfig: $src"
  fi
  # 宿主机后端用 127.0.0.1:6443；Compose 里的 backend 走服务名 k3s（已 --tls-san=k3s）
  sed -e 's#https://127.0.0.1:6443#https://k3s:6443#g' \
      -e 's#https://localhost:6443#https://k3s:6443#g' \
      -e 's#https://0.0.0.0:6443#https://k3s:6443#g' \
      "$src" > "$dst"
  log "已生成 Compose 用 kubeconfig: $dst"
}

reload_backend_if_running() {
  if docker ps --format '{{.Names}}' | grep -qx 'devops-backend'; then
    log "重启 backend 以加载 pipeline.kubeconfig ..."
    export_pipeline_git_http_proxy
    compose up -d --no-deps --force-recreate backend
  else
    log "backend 未在运行。下次 start-dev / start-backend 会自动带上 kubeconfig。"
  fi
}

ensure_job_images() {
  local image_tar="$ROOT_DIR/data/tekton-offline/job-images.tar"
  local list="$ROOT_DIR/deploy/tekton/jobs/images.txt"
  if [ -f "$image_tar" ]; then
    log "导入任务镜像: $image_tar"
    "$SCRIPT_DIR/load-tekton-offline.sh" "$image_tar"
  elif [ -f "$list" ]; then
    log "未找到 job-images.tar，从宿主机拉取平台任务镜像"
    import_images_from_host "$list"
  fi
  local toolchain_tar="$ROOT_DIR/data/tekton-offline/toolchain-images.tar"
  if [ -f "$toolchain_tar" ]; then
    log "导入工具链镜像: $toolchain_tar"
    "$SCRIPT_DIR/load-tekton-offline.sh" "$toolchain_tar"
  fi
}

ensure_tekton_images() {
  local found=0
  local f
  shopt -s nullglob
  for f in "$ROOT_DIR/data/tekton-offline"/*.tar; do
    case "$(basename "$f")" in
      k3s-system-images.tar|.host-import.tar) continue ;;
    esac
    found=1
    break
  done
  shopt -u nullglob
  if [ "$found" -eq 1 ]; then
    "$SCRIPT_DIR/load-tekton-offline.sh"
    return 0
  fi
  local list="$ROOT_DIR/deploy/tekton/pipeline/v1.15.1/images.txt"
  if [ -f "$list" ]; then
    log "未找到 pipeline-images.tar，从宿主机拉取 Tekton 镜像"
    import_images_from_host "$list"
  else
    log "未找到 Tekton 离线 tar，安装时将让 k3s 自己拉 ghcr.io（离线请先 pack-tekton-offline.sh）"
  fi
}

mkdir -p "$ROOT_DIR/data/pipeline" "$ROOT_DIR/data/tekton-offline"
log "启动 k3s + 安装 Tekton（compose profile=pipeline）..."
compose --profile pipeline up -d k3s
i=0
until k3s_kubectl get --raw=/readyz >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    k3s_kubectl get --raw=/readyz || true
    docker logs --tail=40 devops-k3s >&2 || true
    die "k3s API 未就绪（kubectl get --raw=/readyz）。日志见: docker logs devops-k3s"
  fi
  sleep 2
done
ensure_k3s_system_images
ensure_tekton_images
compose --profile pipeline up --abort-on-container-exit tekton-install
write_compose_kubeconfig
log "集群已就绪。kubeconfig: $ROOT_DIR/data/pipeline/kubeconfig.yaml"
reload_backend_if_running
ensure_job_images
log "保存并运行流水线即可提交到 Tekton。"
