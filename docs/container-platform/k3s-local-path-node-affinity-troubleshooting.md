# k3s 容器重启导致 Prometheus Pod Pending 故障排查

> 日期：2026-09-15
> 版本：v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-15 | 初版：k3s 重启后 Prometheus Pod Pending 的排查与修复 |
| v0.2 | 2026-09-15 | docker-compose.yml 添加 hostname: k3s-server 作为预防措施 |

---

## 1. 问题描述

### 现象

Prometheus Pod `prometheus-prometheus-kube-prometheus-prometheus-0` 处于 `Pending` 状态超过 23 小时，无法正常启动。

```bash
$ kubectl -n monitoring get po
NAME                                                   READY   STATUS    RESTARTS   AGE
prometheus-kube-prometheus-operator-58dcb89db5-v7vbv   1/1     Running   0          23h
prometheus-kube-state-metrics-5fc548f4d5-trhjs         1/1     Running   0          23h
prometheus-prometheus-kube-prometheus-prometheus-0     0/2     Pending   0          23h
prometheus-prometheus-node-exporter-pn8s2              1/1     Running   0          23h
```

### 环境

- 集群环境：macOS 上 Docker 里的 k3s（`devops-k3s` 容器）
- 存储方案：k3s 内置 `local-path` 存储类
- 监控栈：kube-prometheus-stack（Prometheus Operator）
- 故障持续时间：约 23 小时

## 2. 排查步骤

### 2.1 定位故障 Pod 事件

使用 `describe pod` 查看调度事件：

```bash
docker exec devops-k3s kubectl -n monitoring describe pod \
  prometheus-prometheus-kube-prometheus-prometheus-0 | grep -A 20 Events
```

输出关键信息：

```
Events:
  Type     Reason            Age                  From               Message
  ----     ------            ----                 ----               -------
  Warning  FailedScheduling  13m (x12 over 137m)  default-scheduler  0/1 nodes are available:
    1 node(s) had volume node affinity conflict.
    preemption: 0/1 nodes are available: 1 Preemption is not helpful for scheduling.
```

**关键线索**：`volume node affinity conflict`——Pod 无法调度是因为卷的节点亲和性冲突，而非资源不足。

### 2.2 确认 PVC / PV 绑定状态

```bash
docker exec devops-k3s kubectl -n monitoring get pvc,pv -o wide
```

结果确认 PVC 处于 `Bound` 状态，PV 也已创建，但问题在于 PV 的节点亲和性与当前节点不匹配。

### 2.3 检查 PV 节点亲和性

```bash
docker exec devops-k3s kubectl get pv pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d -o yaml | grep -A 10 nodeAffinity
```

```yaml
nodeAffinity:
  required:
    nodeSelectorTerms:
    - matchExpressions:
      - key: kubernetes.io/hostname
        operator: In
        values:
        - c66a9331e2e1    # ← 旧的容器 hostname
```

### 2.4 检查当前节点名称

```bash
docker exec devops-k3s kubectl get nodes -o wide
```

```
NAME           STATUS   ROLES                  AGE   VERSION        INTERNAL-IP   OS-IMAGE
c3c3cfecae67   Ready    control-plane,master   23h   v1.31.4+k3s1   172.19.0.2    K3s v1.31.4+k3s1
```

**发现**：PV 的 `nodeAffinity` 指向 `c66a9331e2e1`，但当前节点名为 `c3c3cfecae67`。

### 2.5 确认数据完整性

检查 `local-path` 存储路径是否存在对应的 Prometheus 数据：

```bash
docker exec devops-k3s ls /var/lib/rancher/k3s/storage/ | grep prometheus
```

输出确认数据目录存在（格式为 `{pv-id}_{namespace}_{pvc-name}`）：
```
pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d_monitoring_prometheus-...
```

## 3. 根因分析

### 3.1 触发条件

Docker 容器重启后（无论何种原因重启），Docker 会为容器生成新的 ID。在 k3s 中，节点名称默认取自容器 hostname（即容器 ID），因此重启后节点 hostname 会变化。

### 3.2 故障链路

```
k3s 容器重启
  → 容器 hostname 从 c66a9331e2e1 变为 c3c3cfecae67
  → k3s 以新 hostname 注册为 k8s 节点（新节点）
  → local-path-provisioner 创建的 PV 的 nodeAffinity 仍写旧 hostname
  → Prometheus Pod 引用该 PV，但调度器找不到匹配 nodeAffinity 的节点
  → Pod 一直 Pending
```

### 3.3 影响范围

`local-path` 存储类创建的 PV 都有 `nodeAffinity` 绑定到创建时的节点。因此**所有使用 `local-path` PV 的有状态服务**理论上都可能受此影响，只是 Prometheus 仅 1 副本，所以最先暴露。但若无新容器调度（StatefulSet 未删除重建），其他服务仍可正常运行。

## 4. 解决思路

### 4.1 难点

- PV 的 `nodeAffinity` 字段在 Kubernetes 中**不可原地修改**（immutable）
- `local-path` 的默认 `ReclaimPolicy` 为 `Delete`，直接删除 PV 会丢失数据
- 不能通过 `kubectl edit` 或 `kubectl patch` 直接改 nodeAffinity

### 4.2 可选方案

| 方案 | 优点 | 缺点 |
|------|------|------|
| **A. 删除 PVC → StatefulSet 重建** | 操作简单 | 数据丢失（reclaimPolicy=Delete） |
| **B. 修改 ReclaimPolicy → 删重建 PV** | 保留数据 | 步骤较多 |
| **C. 解绑 PVC → 重建 PV 后重新绑定** | 保留数据 | 需要手动管理 PVC/PV 生命周期 → **最终采用** |

最终采用 **方案 B**：先将 PV 的 `ReclaimPolicy` 改为 `Retain`，然后安全地删除并重建 PV，再触发 PVC 重新绑定。

## 5. 解决步骤

### 步骤 1：修改 PV 回收策略为 Retain

防止后续删除 PV 时 local-path provisioner 清理底层数据：

```bash
docker exec devops-k3s kubectl patch pv pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d \
  -p '{"spec":{"persistentVolumeReclaimPolicy":"Retain"}}'
```

### 步骤 2：删除旧 PV

PV 有 `kubernetes.io/pv-protection` finalizer，删除后处于 `Terminating` 状态，需要移除 finalizer：

```bash
# 删除 PV（会进入 Terminating）
docker exec devops-k3s kubectl delete pv pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d --wait=false

# 移除 finalizer 完成删除
docker exec devops-k3s kubectl patch pv pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d \
  -p '{"metadata":{"finalizers":null}}' --type=merge
```

### 步骤 3：创建新 PV（修正 nodeAffinity）

基于旧 PV 的完整规格重新创建，将 `nodeAffinity` 中的 hostname 改为当前节点：

```bash
docker exec -i devops-k3s kubectl create -f - <<'EOF'
apiVersion: v1
kind: PersistentVolume
metadata:
  annotations:
    local.path.provisioner/selected-node: c3c3cfecae67
    pv.kubernetes.io/provisioned-by: rancher.io/local-path
  name: pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d
spec:
  accessModes:
  - ReadWriteOnce
  capacity:
    storage: 20Gi
  claimRef:
    apiVersion: v1
    kind: PersistentVolumeClaim
    name: prometheus-prometheus-kube-prometheus-prometheus-db-prometheus-prometheus-kube-prometheus-prometheus-0
    namespace: monitoring
  local:
    path: /var/lib/rancher/k3s/storage/pvc-9aa919cd-89bf-4b33-a4df-2a17549b6d7d_monitoring_prometheus-prometheus-kube-prometheus-prometheus-db-prometheus-prometheus-kube-prometheus-prometheus-0
  nodeAffinity:
    required:
      nodeSelectorTerms:
      - matchExpressions:
        - key: kubernetes.io/hostname
          operator: In
          values:
          - c3c3cfecae67  # ← 修正为当前节点 hostname
  persistentVolumeReclaimPolicy: Retain
  storageClassName: local-path
  volumeMode: Filesystem
EOF
```

### 步骤 4：触发 PVC 重建

删除 PVC 后，Prometheus Operator 或 StatefulSet 控制器会自动重建 PVC，并绑定到步骤 3 创建的 PV：

```bash
docker exec devops-k3s kubectl -n monitoring delete pvc \
  prometheus-prometheus-kube-prometheus-prometheus-db-prometheus-prometheus-kube-prometheus-prometheus-0
```

等待自动重建完成：

```bash
docker exec devops-k3s kubectl -n monitoring get pvc,pv -o wide | grep prometheus
```

预期输出（PVC 已绑定到新 PV）：

```
persistentvolumeclaim/prometheus-...-0   Bound    pvc-9aa919cd-...   20Gi   RWO   local-path
persistentvolume/pvc-9aa919cd-...        Bound    monitoring/prometheus-...-0  20Gi   RWO   Retain
```

### 步骤 5：验证 Pod 恢复

```bash
# 查看 Pod 调度事件
docker exec devops-k3s kubectl -n monitoring describe pod \
  prometheus-prometheus-kube-prometheus-prometheus-0 | grep -A 20 Events

# 等待容器 readiness probe 通过（Prometheus 加载数据可能需要几秒到几十秒）
sleep 15 && docker exec devops-k3s kubectl -n monitoring get po
```

恢复后的正常输出：

```
NAME                                                   READY   STATUS    RESTARTS   AGE
prometheus-kube-prometheus-operator-58dcb89db5-v7vbv   1/1     Running   0          23h
prometheus-kube-state-metrics-5fc548f4d5-trhjs         1/1     Running   0          23h
prometheus-prometheus-kube-prometheus-prometheus-0     2/2     Running   0          23h
prometheus-prometheus-node-exporter-pn8s2              1/1     Running   0          23h
```

## 6. 根本解决方案

### 6.1 防止 k3s 容器重启导致 hostname 变化

k3s 容器使用 `hostname` 固定主机名，使容器重启后节点名不变。**本仓库已应用此修复**（`docker-compose.yml` 的 k3s 服务中添加了 `hostname: k3s-server`）：

```yaml
# docker-compose.yml 中为 k3s 服务设置（已应用）
services:
  k3s:
    hostname: k3s-server  # 固定 hostname，重启不变
```

> 注意：设置 hostname 只**预防新建 PV** 出现 nodeAffinity 冲突。对于已创建的 PV（旧 hostname），仍需要按 §5 修复。

下次 `docker compose up -d k3s` 重建容器后，k8s 节点名将固定为 `k3s-server`，不再随容器 ID 变化。

### 6.2 使用支持跨节点迁移的存储

对于生产或重要环境，考虑：

- **NFS / NAS** 存储后端（如 NFS CSI driver）
- **Longhorn**（Rancher 出品，与 k3s 集成良好，支持跨节点自动迁移）
- **Rook / Ceph**（更重型的分布式存储方案）

`local-path` 适合开发测试环境，其 PV 的 nodeAffinity 天然绑定到创建节点，容器重启后必然失效。

### 6.3 自动化恢复脚本

如果固定 hostname 不可行，可编写脚本在 k3s 容器启动后自动修正所有 `local-path` PV 的 nodeAffinity：

```bash
#!/bin/bash
# 容器启动后修正所有 local-path PV 的 nodeAffinity
CURRENT_NODE=$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')
kubectl get pv -o json | jq -r '
  .items[] | select(.spec.storageClassName == "local-path") | .metadata.name
' | while read pv; do
  OLD_NODE=$(kubectl get pv "$pv" -o jsonpath='{.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0]}')
  if [ "$OLD_NODE" != "$CURRENT_NODE" ]; then
    echo "Fixing PV $pv: $OLD_NODE -> $CURRENT_NODE"
    # 执行本文档所述修复步骤
  fi
done
```

## 7. 经验总结

| 项目 | 内容 |
|------|------|
| **根因** | k3s 容器重启 → 节点 hostname 变化 → local-path PV 的 nodeAffinity 不匹配 |
| **判定依据** | `describe pod` 的 `volume node affinity conflict` 事件 |
| **修复代价** | 无数据丢失，仅 Pod 调度恢复耗时约 2 分钟 |
| **适用范围** | 所有使用 `local-path` 存储类的有状态工作负载 |
| **规避手段** | `docker-compose` 中指定 `hostname`，或使用跨节点存储后端 |