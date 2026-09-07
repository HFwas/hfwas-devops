#!/bin/sh
set -eu

KUBE_SRC=/kube/kubeconfig.yaml
KUBE=/tmp/kubeconfig.yaml
KUBECTL="k3s kubectl --kubeconfig=$KUBE"

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

TEKTON_RELEASE="${TEKTON_RELEASE:-https://infra.tekton.dev/tekton-releases/pipeline/previous/v1.15.1/release.yaml}"
echo "installing Tekton from $TEKTON_RELEASE"
$KUBECTL apply --filename "$TEKTON_RELEASE"
$KUBECTL wait --for=condition=Available -n tekton-pipelines --timeout=180s deploy/tekton-pipelines-controller
$KUBECTL wait --for=condition=Available -n tekton-pipelines --timeout=180s deploy/tekton-pipelines-webhook
$KUBECTL create namespace hfwas-pipeline --dry-run=client -o yaml | $KUBECTL apply -f -
echo "Tekton is ready"
