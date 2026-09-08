#!/bin/sh
set -eu

KUBE_SRC=/kube/kubeconfig.yaml
KUBE=/tmp/kubeconfig.yaml
KUBECTL="k3s kubectl --kubeconfig=$KUBE"
TEKTON_VERSION="${TEKTON_VERSION:-v1.15.1}"
LOCAL_RELEASE="/tekton/pipeline/${TEKTON_VERSION}/release.yaml"
DEFAULT_URL="https://infra.tekton.dev/tekton-releases/pipeline/previous/${TEKTON_VERSION}/release.yaml"

echo "waiting for k3s kubeconfig..."
i=0
while [ ! -s "$KUBE_SRC" ]; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "timeout waiting for kubeconfig" >&2
    exit 1
  fi
  sleep 2
done

sed -e 's#https://127.0.0.1:6443#https://k3s:6443#g' \
    -e 's#https://localhost:6443#https://k3s:6443#g' \
    -e 's#https://0.0.0.0:6443#https://k3s:6443#g' \
    "$KUBE_SRC" > "$KUBE"

echo "waiting for kubernetes API..."
i=0
until $KUBECTL get ns >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "timeout waiting for API" >&2
    exit 1
  fi
  sleep 2
done

if [ -n "${TEKTON_RELEASE:-}" ] && [ -f "$TEKTON_RELEASE" ]; then
  RELEASE_FILE=$TEKTON_RELEASE
elif [ -f "$LOCAL_RELEASE" ]; then
  RELEASE_FILE=$LOCAL_RELEASE
elif [ -n "${TEKTON_RELEASE:-}" ]; then
  RELEASE_FILE=$TEKTON_RELEASE
else
  RELEASE_FILE=$DEFAULT_URL
fi

echo "installing Tekton from $RELEASE_FILE"
if ! $KUBECTL apply --filename "$RELEASE_FILE"; then
  echo "apply 失败。离线环境请确认已挂载 deploy/tekton，且 data/tekton-offline/*.tar 已导入 k3s。" >&2
  exit 1
fi
$KUBECTL wait --for=condition=Available -n tekton-pipelines --timeout=180s deploy/tekton-pipelines-controller
$KUBECTL wait --for=condition=Available -n tekton-pipelines --timeout=180s deploy/tekton-pipelines-webhook
$KUBECTL create namespace hfwas-pipeline --dry-run=client -o yaml | $KUBECTL apply -f -
echo "Tekton is ready"
