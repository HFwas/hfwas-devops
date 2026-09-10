# Prometheus 部署文档

> 版本: kube-prometheus-stack 62.x（Prometheus v2.55.x / node-exporter v1.8.x / kube-state-metrics v2.15.x）  
> 集群: k3s v1.31.4（单节点 ARM64 + Rosetta 2 模拟 x86_64）  
> 日期: 2026-09-10

---

## 目录

1. [环境信息](#1-环境信息)
2. [架构概览](#2-架构概览)
3. [安装步骤](#3-安装步骤)
4. [镜像清单](#4-镜像清单)
5. [Java JVM 监控配置](#5-java-jvm-监控配置)
6. [PromQL 查询示例](#6-promql-查询示例)
7. [容器管理平台接入](#7-容器管理平台接入)
8. [升级操作](#8-升级操作)
9. [遇到的问题及解决方法](#9-遇到的问题及解决方法)
10. [验证](#10-验证)
11. [附录 A：传统 JMX Exporter 配置](#附录-a传统-jmx-exporter-配置)
12. [附录 B：指标名称对照表](#附录-b指标名称对照表)
13. [附录 C：常用命令](#附录-c常用命令)

---

## 1. 环境信息

| 项目 | 值 |
|------|-----|
| Kubernetes 集群 | k3s v1.31.4 (单节点) |
| 节点架构 | ARM64 (Apple Silicon) + Rosetta 2 模拟 x86_64 |
| Prometheus 部署方式 | Helm chart (kube-prometheus-stack) |
| 存储驱动 | local-path-provisioner (rancher) |
| 暴露方式 | NodePort (Prometheus UI: 30090, Alertmanager: 30093) |
| Prometheus API 地址 | `http://localhost:30090`（平台 Query 入口） |
| Grafana | **可选**，默认禁用（平台不依赖 Grafana） |

### 监控维度覆盖

| 维度 | 采集组件 | 目标 |
|------|----------|------|
| Node CPU/内存 | node-exporter + kubelet | 节点资源利用率 |
| Node 网络 IO | node-exporter | 节点网络吞吐 |
| Node 连接数 | node-exporter (`node_netstat_Tcp_CurrEstab`) | 节点 TCP 连接数 |
| Pod CPU/内存 | kubelet cAdvisor (内置) + kube-state-metrics | Pod 资源使用 |
| Pod 网络 IO | kubelet cAdvisor (内置) | Pod 网络吞吐 |
| JVM 堆内/堆外/GC | OpenTelemetry Java Agent（推荐）/ JMX Exporter（备选） | Java 服务 JVM 指标 |

---

## 2. 架构概览

```
                    ┌──────────────────────────────────┐
                    │       容器管理平台                 │
                    │  (PromQL 消费层, 不部署 Prometheus) │
                    └──────┬───────────────────────────┘
                           │ HTTP (NodePort 30090)
                           ▼
┌──────────────────────────────────────────────────────────┐
│                   Prometheus (k3s 集群侧)                  │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │  node-exporter│  │ kube-state-  │  │ kubelet        │ │
│  │  (DaemonSet)  │  │ metrics      │  │ (cAdvisor 内置) │ │
│  │  节点指标     │  │ K8s 对象指标  │  │ Pod 容器指标    │ │
│  └──────┬───────┘  └──────┬───────┘  └───────┬────────┘ │
│         └─────────────────┼──────────────────┘          │
│                           ▼                             │
│                    ┌──────────┐                        │
│                    │ TSDB     │                        │
│                    │ (PV 持久化)│                       │
│                    └──────────┘                        │
│                         │                              │
│                         ▼                              │
│                   ┌───────────┐                        │
│                   │ Alertmanager│ (可选, 告警路由)      │
│                   └───────────┘                        │
└──────────────────────────────────────────────────────────┘
         │
         ▼
┌───────────────────────────────────────────┐
│ Java Pod                                  │
│  ┌─────────────────────────────────────┐  │
│  │ App (JVM)                          │  │
│  │                                     │  │
│  │ 【推荐】OTel Java Agent (javaagent) │──┼────► /metrics (Prometheus)
│  │   或                                  │  │
│  │ 【备选】OTel JMX Gatherer (sidecar)  │──┼────► /metrics (Prometheus)
│  └─────────────────────────────────────┘  │
└───────────────────────────────────────────┘
```

### 组件说明

| 组件 | 角色 | 数据来源 |
|------|------|----------|
| **Prometheus** | 指标存储 + PromQL 查询 | TSDB (本地 PV) |
| **node-exporter** | Node 级别指标（CPU/Mem/Net/Disk） | `/proc`, `/sys` |
| **kube-state-metrics** | K8s 对象状态（Pod/Deploy/Node/etc.） | Kubernetes API |
| **kubelet cAdvisor** | 容器运行时指标（CPU/Mem/Net per container） | kubelet 内置 |
| **OpenTelemetry Java Agent**（推荐） | JVM 指标（Heap/Non-heap/GC/Thread），单 javaagent 同时覆盖 Trace + Metrics | Java Attach API，零配置开箱即用 |
| **OTel JMX Gatherer**（备选） | 独立 sidecar 进程通过 JMX RMI 抓取 MBean 指标 | 应用 JMX 端口 (1099) |
| **Alertmanager** | 告警去重/分组/路由（可选） | Prometheus 推送 |

---

## 3. 安装步骤

### 3.1 前置条件

- k3s 集群已运行
- Helm CLI 可用（k3s 内置）
- 本地 Docker 可用（用于拉取镜像导入 k3s）

### 3.2 添加 Helm 仓库

```bash
# 在 k3s 内操作
docker exec devops-k3s helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
docker exec devops-k3s helm repo update
```

### 3.3 创建命名空间

```bash
docker exec devops-k3s kubectl create namespace monitoring
```

### 3.4 准备镜像

由于集群**无互联网访问**，需要在宿主机拉取镜像后导入 k3s containerd。

#### 3.4.1 拉取镜像

```bash
# 在宿主机执行
# kube-prometheus-stack 核心镜像
docker pull quay.io/prometheus/prometheus:v2.55.1
docker pull quay.io/prometheus/node-exporter:v1.8.2
docker pull quay.io/prometheus-operator/prometheus-config-reloader:v0.77.2
docker pull quay.io/prometheus-operator/prometheus-operator:v0.77.2
docker pull registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0
docker pull quay.io/kiwigrid/k8s-sidecar:1.28.0

# Alertmanager（可选）
docker pull quay.io/prometheus/alertmanager:v0.28.1

# Grafana（可选，平台本身不依赖）
# docker pull docker.io/grafana/grafana:11.4.0
```

#### 3.4.2 导入 k3s containerd

```bash
# 逐个导入到 k8s.io 命名空间
docker save quay.io/prometheus/prometheus:v2.55.1 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save quay.io/prometheus/node-exporter:v1.8.2 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save quay.io/prometheus-operator/prometheus-config-reloader:v0.77.2 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save quay.io/prometheus-operator/prometheus-operator:v0.77.2 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save quay.io/kiwigrid/k8s-sidecar:1.28.0 | docker exec -i devops-k3s ctr -n k8s.io images import -
docker save quay.io/prometheus/alertmanager:v0.28.1 | docker exec -i devops-k3s ctr -n k8s.io images import -
```

> **注意**: 必须导入到 `k8s.io` 命名空间（`ctr -n k8s.io`），否则 kubelet 找不到镜像。

### 3.5 准备 Helm Values

创建自定义 values 文件：

```bash
# 宿主机创建 values 文件
cat > /tmp/prometheus-values.yaml << 'EOF'
# Prometheus 监控栈 - k3s 单节点部署 values
# 禁用 Grafana（平台不依赖），启用必要组件

# 全局镜像拉取策略（离线环境必须 Always → 不会触发 ImagePull，用已有镜像）
global:
  imagePullPolicy: IfNotPresent

# ── Prometheus Operator ──
prometheusOperator:
  image:
    registry: quay.io
    repository: prometheus-operator/prometheus-operator
    tag: v0.77.2
  prometheusConfigReloader:
    image:
      registry: quay.io
      repository: prometheus-operator/prometheus-config-reloader
      tag: v0.77.2
  # ARM64 兼容
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: kubernetes.io/arch
            operator: In
            values:
            - amd64
            - arm64

# ── Prometheus ──
prometheus:
  enabled: true
  agentMode: false
  image:
    registry: quay.io
    repository: prometheus/prometheus
    tag: v2.55.1
  
  # 禁用一些默认的 ServiceMonitor（避免 CRD 不兼容）
  defaultRules:
    create: false
  
  # 禁用 alertmanager 相关 rules 和 service
  alertmanager:
    enabled: false
  
  # 存储配置（使用 local-path）
  prometheusSpec:
    storageSpec:
      volumeClaimTemplate:
        spec:
          storageClassName: local-path
          accessModes: ["ReadWriteOnce"]
          resources:
            requests:
              storage: 20Gi
  
  # NodePort 暴露（平台通过此地址查询 PromQL）
  service:
    type: NodePort
    nodePort: 30090
  
  # 采集配置
  retention: 15d
  retentionSize: 18GB
  
  # 资源限制（单节点 dev 环境设低一些）
  resources:
    requests:
      memory: 512Mi
      cpu: 200m
    limits:
      memory: 2Gi
      cpu: "1"
  
  # 允许采集所有 namespace 的 Pod
  scrapeConfigNamespaceSelector: {}
  ruleNamespaceSelector: {}
  serviceMonitorNamespaceSelector: {}
  podMonitorNamespaceSelector: {}
  
  # 额外 ScrapeConfig（后续可直接修改 ConfigMap 或加 ServiceMonitor）
  additionalScrapeConfigs:
    # node-exporter 已由 Prometheus 默认 job 采集，无需额外配置
    # kubelet/cAdvisor 已由 Prometheus operator 自动注册
    - job_name: 'prometheus-self'
      static_configs:
        - targets: ['localhost:9090']

# ── Node Exporter ──
nodeExporter:
  enabled: true
  image:
    registry: quay.io
    repository: prometheus/node-exporter
    tag: v1.8.2
  
  # 收集 TCP 连接数等额外指标
  extraArgs:
    - --collector.netstat
    - --collector.tcpstat
  
  # tolerations 允许调度到所有节点
  tolerations:
    - operator: Exists
  
  # ARM64 兼容
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: kubernetes.io/arch
            operator: In
            values:
            - amd64
            - arm64

# ── kube-state-metrics ──
kubeStateMetrics:
  enabled: true
  image:
    registry: registry.k8s.io
    repository: kube-state-metrics/kube-state-metrics
    tag: v2.15.0

# ── Alertmanager（可选，默认禁用）──
alertmanager:
  enabled: false

# ── Grafana（可选，默认禁用）──
grafana:
  enabled: false
EOF
```

> **注意**: 以上 values 禁用了 Grafana 和 Alertmanager。如果需要告警功能，可参考附录启用的 values。

### 3.6 安装 kube-prometheus-stack

```bash
# 将 values 文件复制到 k3s 容器
docker cp /tmp/prometheus-values.yaml devops-k3s:/tmp/prometheus-values.yaml

# 在 k3s 中执行 Helm 安装
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm install prometheus prometheus-community/kube-prometheus-stack --version 62.x \
  -n monitoring \
  -f /tmp/prometheus-values.yaml'
```

### 3.7 验证安装

```bash
# 查看 Pod 状态
docker exec devops-k3s kubectl -n monitoring get pods -w

# 查看 Service
docker exec devops-k3s kubectl -n monitoring get svc

# 所有 Pod Running 后，验证 Prometheus API
curl http://localhost:30090/api/v1/query?query=up
```

期望输出（所有目标 `up == 1`）：
```json
{
  "status": "success",
  "data": {
    "result": [
      {"metric": {"instance": "localhost:9090", "job": "prometheus-self"}, "value": [..., "1"]},
      {"metric": {"instance": "xxx:9100", "job": "node-exporter"}, "value": [..., "1"]},
      {"metric": {"instance": "xxx:8080", "job": "kube-state-metrics"}, "value": [..., "1"]}
    ]
  }
}
```

---

## 4. 镜像清单

### 4.1 kube-prometheus-stack 组件镜像（7 个）

| # | 镜像 | 组件 | 大小 |
|---|------|------|------|
| 1 | `quay.io/prometheus/prometheus:v2.55.1` | Prometheus 核心 | ~400MB |
| 2 | `quay.io/prometheus/node-exporter:v1.8.2` | 节点指标采集器 | ~50MB |
| 3 | `quay.io/prometheus-operator/prometheus-operator:v0.77.2` | Operator 控制器 | ~200MB |
| 4 | `quay.io/prometheus-operator/prometheus-config-reloader:v0.77.2` | 配置热加载器 | ~100MB |
| 5 | `registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0` | K8s 对象指标 | ~100MB |
| 6 | `quay.io/kiwigrid/k8s-sidecar:1.28.0` | ConfigMap 同步（可选） | ~50MB |
| 7 | `quay.io/prometheus/alertmanager:v0.28.1` | 告警管理器（可选） | ~100MB |

### 4.2 Java 监控镜像

> 用于 Java Pod JVM 指标采集。**推荐使用 OpenTelemetry Java Agent**。

| # | 镜像 | 方案 | 大小 |
|---|------|------|------|
| 1 | `docker.io/otel/opentelemetry-java-agent:2.12.0` | **OTel Java Agent**（推荐，零配置，单 agent 同时采集 Trace+Metrics） | ~50MB |
| 2 | `docker.io/otel/opentelemetry-jmx-metrics:2.0.0` | OTel JMX Gatherer（备选，独立 sidecar 进程） | ~100MB |
| 3 | `docker.io/bitnami/jmx-exporter:1.0.1` | 传统 JMX Exporter（备选，仅 Prometheus 格式） | ~200MB |

### 4.3 k3s 系统组件镜像（已预装）

| # | 镜像 | 组件 |
|---|------|------|
| 1 | `rancher/mirrored-metrics-server:v0.7.2` | Metrics Server（k3s 内置） |

> Metrics Server 由 k3s 默认部署，提供 `kubectl top` 所需的 Pod/Node CPU/内存瞬时值，但 **不提供历史趋势**（Prometheus 提供）。

---

## 5. Java JVM 监控配置

JVM 监控提供两种方案，按优先级排列：

| 方案 | 方式 | 适用场景 |
|------|------|----------|
| **方案一：OTel Java Agent**（**推荐**） | javaagent 注入，零配置，单 agent 同时采集 Trace + Metrics | 应用可添加 `JAVA_TOOL_OPTIONS` 环境变量 |
| **方案二：OTel JMX Gatherer**（备选） | 独立 sidecar 容器通过 JMX RMI 连接应用 | 应用无法修改启动参数，但开启了 JMX 端口 |
| **方案三：传统 JMX Exporter**（备选） | javaagent 注入，仅 Prometheus 格式 | 遗留系统，兼容已有配置 |

> **推荐优先使用方案一**：OpenTelemetry Java Agent 内建了 JVM 运行时指标采集能力，**零配置开箱即用**，且未来可以无缝切换到 OTLP 协议直推，不需要改 Pod 配置。

---

### 方案一（推荐）：OpenTelemetry Java Agent

#### 5.1 工作原理

OpenTelemetry Java Agent 是一个 javaagent，在 JVM 启动时自动注册 `MeterProvider`，通过 Micrometer 内置 instrumentation 采集 JVM 指标，以 Prometheus 格式暴露 HTTP 端点。

```
Java 应用 Pod
┌──────────────────────────────────────┐
│  ┌──────────────────────────────┐   │
│  │  App (JVM)                   │   │
│  │                              │   │
│  │  + opentelemetry-javaagent   │───┼────► /metrics (Prometheus)
│  │    (内置 JVM metrics)         │   │
│  └──────────────────────────────┘   │
│      端口 9404                       │
└──────────────────────────────────────┘
         │
         ▼
   Prometheus scrape (annotation: prometheus.io/scrape=true)
```

**OTel Java Agent 内置的 JVM 指标（零配置自动采集）：**

| 指标 | Prometheus 名称 | 说明 |
|------|-----------------|------|
| 堆内存 | `jvm_memory_used_bytes{area="heap"}` | 已用堆内存 |
| 非堆内存 | `jvm_memory_used_bytes{area="nonheap"}` | 已用非堆内存（Metaspace/CodeCache） |
| 内存池 | `jvm_memory_pool_used_bytes{pool="..."}` | G1 Eden / Survivor / Old / Metaspace |
| GC 持续时间 | `jvm_gc_duration_seconds_*` | GC 暂停耗时直方图 |
| GC 次数 | `jvm_gc_collections_count_total` | GC 发生次数 |
| 内存承诺/上限 | `jvm_memory_committed_bytes` / `jvm_memory_limit_bytes` | 承诺内存与上限 |
| 线程数 | `jvm_threads_count` / `jvm_threads_started_total` | 线程统计 |
| 类加载 | `jvm_classes_loaded_classes` / `jvm_classes_unloaded_total` | 类加载统计 |
| CPU | `jvm_cpu_recent_utilization_ratio` | JVM CPU 利用率 |
| 缓冲区 | `jvm_buffer_pool_*` | Direct Buffer 等 |

不需要配置 JMX 规则，不需要定义 MBean pattern，**加 agent 即可**。

#### 5.2 OTel Java Agent 镜像导入

```bash
# 宿主机拉取
docker pull docker.io/otel/opentelemetry-java-agent:2.12.0

# 导入 k3s
docker save docker.io/otel/opentelemetry-java-agent:2.12.0 | docker exec -i devops-k3s ctr -n k8s.io images import -
```

#### 5.3 在 Java Pod 中注入 OTel Java Agent

在 Java 应用的 Deployment/StatefulSet 中，挂载 agent jar 并通过 `JAVA_TOOL_OPTIONS` 注入：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: java-app
  namespace: hfwas-pipeline
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "9404"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: java-app
  template:
    metadata:
      labels:
        app: java-app
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9404"
    spec:
      # ═══ InitContainer: 将 agent jar 拷贝到共享卷 ═══
      initContainers:
        - name: otel-agent-copy
          image: otel/opentelemetry-java-agent:2.12.0
          command: ["cp", "/javaagent/opentelemetry-javaagent.jar", "/otel-agent/"]
          volumeMounts:
            - name: otel-agent
              mountPath: /otel-agent
      
      containers:
        # 主应用容器
        - name: app
          image: your-java-app:latest
          env:
            # 关键：通过 JAVA_TOOL_OPTIONS 注入 OTel Java Agent
            - name: JAVA_TOOL_OPTIONS
              value: "-javaagent:/otel-agent/opentelemetry-javaagent.jar"
            # 以 Prometheus 协议暴露指标（供 Prometheus scrape）
            - name: OTEL_METRICS_EXPORTER
              value: "prometheus"
            - name: OTEL_EXPORTER_PROMETHEUS_HOST
              value: "0.0.0.0"
            - name: OTEL_EXPORTER_PROMETHEUS_PORT
              value: "9404"
            # 服务标识（用于 label 过滤）
            - name: OTEL_SERVICE_NAME
              value: "java-app"
            # 禁用 OTLP 导出的默认 exporter（只用 Prometheus）
            - name: OTEL_TRACES_EXPORTER
              value: "none"
            - name: OTEL_LOGS_EXPORTER
              value: "none"
          ports:
            - containerPort: 9404
              name: metrics
          volumeMounts:
            - name: otel-agent
              mountPath: /otel-agent
              readOnly: true
      
      volumes:
        - name: otel-agent
          emptyDir: {}
```

> **关键说明**:
> - **InitContainer** 从 `otel/opentelemetry-java-agent` 镜像中复制 agent jar 到 Pod 共享卷（agent 镜像自带 jar，无需额外打包）
> - **`JAVA_TOOL_OPTIONS`** 自动追加 javaagent 参数，应用无需修改 Dockerfile
> - **`OTEL_METRICS_EXPORTER=prometheus`** 让 agent 在 9404 端口暴露 `/metrics` 端点
> - Metadata annotation `prometheus.io/scrape=true` 通知 Prometheus 自动发现采集
> - **不需要** 额外的 ConfigMap 或 JMX 配置规则

#### 5.4 进阶配置

如需采集更丰富的指标或未来迁移到 OTLP 协议，可通过环境变量控制：

```yaml
env:
  # 当前：Prometheus 协议暴露（供 Prometheus 采集）
  - name: OTEL_METRICS_EXPORTER
    value: "prometheus"
  - name: OTEL_EXPORTER_PROMETHEUS_HOST
    value: "0.0.0.0"
  - name: OTEL_EXPORTER_PROMETHEUS_PORT
    value: "9404"
  
  # 未来：同时直推 OTLP（不用改代码，加下行即可）
  # - name: OTEL_EXPORTER_OTLP_ENDPOINT
  #   value: "http://otel-collector:4318"
  
  # 服务名（用于区分不同应用）
  - name: OTEL_SERVICE_NAME
    value: "my-java-service"
  
  # 资源属性（附加标签）
  - name: OTEL_RESOURCE_ATTRIBUTES
    value: "deployment.environment=production,service.namespace=hfwas-pipeline"
```

---

### 方案二（备选）：OTel JMX Gatherer 独立 sidecar

适用于应用**不能修改启动参数**（如第三方镜像），但开启了 JMX RMI 端口的场景。

#### 5.5 OTel JMX Gatherer 工作原理

```
Java 应用 Pod
┌────────────────────────────────────────┐
│  ┌──────────────┐   ┌───────────────┐ │
│  │  App (JVM)   │   │ OTel JMX     │ │
│  │              │   │ Gatherer      │ │
│  │ JMX RMI :1099│◄──│ (sidecar)     │ │
│  │              │   │               │─┼──► /metrics (Prometheus)
│  └──────────────┘   └───────────────┘ │
└────────────────────────────────────────┘
```

#### 5.6 OTel JMX Gatherer 镜像导入

```bash
docker pull docker.io/otel/opentelemetry-jmx-metrics:2.0.0
docker save docker.io/otel/opentelemetry-jmx-metrics:2.0.0 | docker exec -i devops-k3s ctr -n k8s.io images import -
```

#### 5.7 Pod 配置示例

```yaml
spec:
  containers:
    # 主应用（需开启 JMX RMI 端口）
    - name: app
      image: your-java-app:latest
      env:
        - name: JAVA_TOOL_OPTIONS
          value: "-Dcom.sun.management.jmxremote=true
                  -Dcom.sun.management.jmxremote.port=1099
                  -Dcom.sun.management.jmxremote.rmi.port=1099
                  -Dcom.sun.management.jmxremote.authenticate=false
                  -Dcom.sun.management.jmxremote.ssl=false
                  -Djava.rmi.server.hostname=127.0.0.1"
      ports:
        - containerPort: 1099
    
    # OTel JMX Gatherer sidecar
    - name: otel-jmx
      image: otel/opentelemetry-jmx-metrics:2.0.0
      env:
        - name: OTEL_JMX_TARGET
          value: "localhost:1099"
        - name: OTEL_METRICS_EXPORTER
          value: "prometheus"
        - name: OTEL_EXPORTER_PROMETHEUS_HOST
          value: "0.0.0.0"
        - name: OTEL_EXPORTER_PROMETHEUS_PORT
          value: "9404"
      ports:
        - containerPort: 9404
          name: metrics
```

---

### 方案三（备选）：传统 JMX Exporter

保留了原有 JMX Exporter 方案，适用于已有配置的遗留系统。

#### 5.8 镜像导入

```bash
docker pull docker.io/bitnami/jmx-exporter:1.0.1
docker save docker.io/bitnami/jmx-exporter:1.0.1 | docker exec -i devops-k3s ctr -n k8s.io images import -
```

#### 5.9 JMX Exporter 配置 + Pod 注入

参见 [附录：传统 JMX Exporter 配置](#附录传统-jmx-exporter-配置)。

---

### 5.10 配置 Prometheus 采集 Java Pod（所有方案通用）

无论哪种方案，Java Pod 都在 `9404` 端口暴露 `/metrics`，通过 Pod annotation 自动发现。

```bash
# 通过 Prometheus additionalScrapeConfigs 添加自动发现
cat > /tmp/prometheus-values-otel.yaml << 'EOF'
prometheus:
  prometheusSpec:
    additionalScrapeConfigs:
      # 采集带 prometheus.io/scrape=true 注解的 Pod（适用 OTel Agent / JMX Gatherer / JMX Exporter）
      - job_name: 'kubernetes-pods-metrics'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
            action: keep
            regex: true
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
            action: replace
            target_label: __metrics_path__
            regex: (.+)
          - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
            action: replace
            regex: ([^:]+)(?::\d+)?;(\d+)
            replacement: $1:$2
            target_label: __address__
          - action: labelmap
            regex: __meta_kubernetes_pod_label_(.+)
          - source_labels: [__meta_kubernetes_namespace]
            action: replace
            target_label: kubernetes_namespace
          - source_labels: [__meta_kubernetes_pod_name]
            action: replace
            target_label: kubernetes_pod_name
EOF

docker cp /tmp/prometheus-values-otel.yaml devops-k3s:/tmp/prometheus-values-otel.yaml

# Helm upgrade 应用新配置
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm upgrade prometheus prometheus-community/kube-prometheus-stack --version 62.x \
  -n monitoring \
  -f /tmp/prometheus-values.yaml \
  -f /tmp/prometheus-values-otel.yaml'
```

> **说明**: 应用只需添加对应 Pod annotation `prometheus.io/scrape=true` + `prometheus.io/port=9404`，Prometheus 自动发现采集。

### 5.11 验证 JVM 指标采集

```bash
# 查看 Prometheus 是否发现 Java Pod 目标
curl -s 'http://localhost:30090/api/v1/targets' | jq '.data.activeTargets[] | select(.labels.job=="kubernetes-pods-metrics") | .labels.kubernetes_pod_name'

# 查询 JVM 堆内存使用
curl -s "http://localhost:30090/api/v1/query?query=jvm_memory_used_bytes{area=\"heap\"}" | jq .

# 查询 GC 次数
curl -s "http://localhost:30090/api/v1/query?query=rate(jvm_gc_collections_count_total[5m])" | jq .

# 查询各内存池
curl -s 'http://localhost:30090/api/v1/query?query=jvm_memory_pool_used_bytes' | jq '.data.result[].metric.pool'

# 查询 OTel 版本信息
curl -s 'http://localhost:30090/api/v1/query?query=otel_scope_info' | jq .
```

---

## 6. PromQL 查询示例

### 6.1 Node 监控

#### CPU 使用率

```promql
# Node CPU 使用率（百分比）
100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# 每个核心的 CPU 使用率
100 - (avg by(instance, cpu) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

#### 内存使用

```promql
# Node 内存使用率（百分比）
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# Node 内存使用量（GB）
(node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / 1024 / 1024 / 1024

# Node 总内存（GB）
node_memory_MemTotal_bytes / 1024 / 1024 / 1024
```

#### 网络 IO

```promql
# Node 网络接收速率（bytes/s）
rate(node_network_receive_bytes_total[5m])

# Node 网络发送速率（bytes/s）
rate(node_network_transmit_bytes_total[5m])

# 按网卡维度
rate(node_network_receive_bytes_total{device!="lo"}[5m])
rate(node_network_transmit_bytes_total{device!="lo"}[5m])
```

#### 连接数

```promql
# TCP 连接数（需要 node-exporter --collector.netstat 参数）
node_netstat_Tcp_CurrEstab

# TCP 各种状态连接数
node_sockstat_TCP_alloc
node_sockstat_TCP_inuse

# 连接速率
rate(node_netstat_Tcp_RetransSegs[5m])
rate(node_netstat_Tcp_OutSegs[5m])
rate(node_netstat_Tcp_InSegs[5m])
```

#### 磁盘 IO

```promql
# 磁盘读写速率（bytes/s）
rate(node_disk_read_bytes_total[5m])
rate(node_disk_written_bytes_total[5m])

# 磁盘 IO 使用率（%）
rate(node_disk_io_time_seconds_total[5m]) * 100

# 磁盘空间使用率（%）
100 - (node_filesystem_free_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"}) * 100
```

### 6.2 Pod 监控

#### CPU 使用率

```promql
# Pod CPU 使用率（相对于 request/limit）
# 需要 Pod 设置了 resource requests
sum(rate(container_cpu_usage_seconds_total{container!="",container!="POD"}[5m])) by (pod, namespace)
  / 
sum(kube_pod_container_resource_requests{resource="cpu"}) by (pod, namespace)

# Pod CPU 使用量（millicores）
sum(rate(container_cpu_usage_seconds_total{container!="",container!="POD"}[5m])) by (pod, namespace) * 1000

# Pod CPU Throttling（被限流时间占比）
sum(increase(container_cpu_cfs_throttled_seconds_total{container!=""}[5m])) by (pod, namespace)
  /
sum(increase(container_cpu_cfs_periods_total{container!=""}[5m]) * 0.1) by (pod, namespace)
```

#### 内存使用

```promql
# Pod 内存使用量（bytes）
sum(container_memory_working_set_bytes{container!="",container!="POD"}) by (pod, namespace)

# Pod 内存使用率（相对于 limit）
sum(container_memory_working_set_bytes{container!="",container!="POD"}) by (pod, namespace)
  /
sum(kube_pod_container_resource_limits{resource="memory"}) by (pod, namespace) * 100

# Pod RSS 内存
sum(container_memory_rss{container!="",container!="POD"}) by (pod, namespace)

# Pod 缓存
sum(container_memory_cache{container!="",container!="POD"}) by (pod, namespace)
```

#### 网络 IO

```promql
# Pod 网络接收速率（bytes/s）
sum(rate(container_network_receive_bytes_total[5m])) by (pod, namespace)

# Pod 网络发送速率（bytes/s）
sum(rate(container_network_transmit_bytes_total[5m])) by (pod, namespace)

# Pod 网络丢包
sum(rate(container_network_receive_drop_total[5m])) by (pod, namespace)
sum(rate(container_network_transmit_drop_total[5m])) by (pod, namespace)
```

### 6.3 JVM 监控（Java 服务）

> 以下 PromQL 针对 **OpenTelemetry Java Agent**（推荐方案）的指标名称。
> 传统 JMX Exporter 的指标名称不同，参见 [附录：指标名称对照表](#附录指标名称对照表)。

#### 堆内内存

```promql
# JVM 堆内存已用（bytes）
jvm_memory_used_bytes{area="heap"}

# JVM 堆内存上限（bytes）
jvm_memory_limit_bytes{area="heap"}

# JVM 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_limit_bytes{area="heap"} * 100

# 各内存池使用量（G1 Eden / Survivor / Old）
jvm_memory_pool_used_bytes

# 各代内存使用（Eden / Survivor / Old / Metaspace）
jvm_memory_pool_used_bytes{pool=~".*(Eden|Survivor|Old|Metaspace).*"}

# 承诺内存 vs 实际使用
jvm_memory_committed_bytes{area="heap"} - jvm_memory_used_bytes{area="heap"}
```

#### 堆外内存

```promql
# JVM 非堆内存已用（bytes）
jvm_memory_used_bytes{area="nonheap"}

# Metaspace 使用量
jvm_memory_pool_used_bytes{pool="Metaspace"}

# Code Cache
jvm_memory_pool_used_bytes{pool=~"Code.*"}

# Compressed Class Space
jvm_memory_pool_used_bytes{pool=~"Compressed.*"}
```

#### GC 监控

```promql
# GC 次数（rate）
rate(jvm_gc_collections_count_total[5m])

# GC 总耗时（seconds/s）
rate(jvm_gc_collections_elapsed_total[5m])

# Young GC 次数
rate(jvm_gc_collections_count_total{gc="G1 Young Generation"}[5m])

# Full GC 次数
rate(jvm_gc_collections_count_total{gc="G1 Old Generation"}[5m])

# GC 持续时间（直方图，P99）
jvm_gc_duration_seconds{quantile="0.99"}

# Full GC 耗时占比
rate(jvm_gc_collections_elapsed_total{gc="G1 Old Generation"}[5m]) / 60 * 100
```

#### 线程

```promql
# 当前线程数
jvm_threads_count

# 守护线程数
jvm_threads_daemon_count

# 累计启动线程数
rate(jvm_threads_started_total[5m])
```

#### 类加载

```promql
# 已加载类数
jvm_classes_loaded_classes

# 已卸载类数（累计）
jvm_classes_unloaded_total
```

#### CPU

```promql
# JVM 进程 CPU 利用率
jvm_cpu_recent_utilization_ratio

# JVM 可用 CPU 数
jvm_cpu_count
```

#### 缓冲区

```promql
# Direct Buffer / Mapped Buffer 使用
jvm_buffer_pool_used_bytes
jvm_buffer_pool_total_capacity_bytes
```

---

## 7. 容器管理平台接入

### 7.1 配置平台 Prometheus 数据源

容器管理平台（后端 Java 服务）通过 Prometheus HTTP API（NodePort 30090）获取指标数据。

```yaml
# 容器平台后端配置（application-prod.yml 或环境变量）
prometheus:
  enabled: true
  host: "http://<k3s-node-ip>:30090"
  # 如果平台以 Docker Compose 运行，在宿主机访问 k3s NodePort
  # host: "http://host.docker.internal:30090"   # macOS
  # host: "http://172.17.0.1:30090"              # Linux bridge
  timeout: 30s
  max-query-concurrency: 10
```

### 7.2 平台需实现的监控接口

| 维度 | 接口 | PromQL | 说明 |
|------|------|--------|------|
| **节点列表** | `GET /api/monitor/nodes` | `kube_node_info` | 获取所有节点基本信息 |
| **Node CPU** | `GET /api/monitor/nodes/{name}/cpu` | `100 - avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100` | Node CPU 使用率时序 |
| **Node 内存** | `GET /api/monitor/nodes/{name}/memory` | `(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100` | Node 内存使用率 |
| **Node 网络** | `GET /api/monitor/nodes/{name}/network` | `rate(node_network_receive_bytes_total[5m])`, `rate(node_network_transmit_bytes_total[5m])` | Node 网络 IO 时序 |
| **Node 连接数** | `GET /api/monitor/nodes/{name}/connections` | `node_netstat_Tcp_CurrEstab` | TCP 连接数 |
| **Pod CPU** | `GET /api/monitor/pods/{ns}/{name}/cpu` | `sum(rate(container_cpu_usage_seconds_total{container!=""}[5m])) by (pod)` | Pod CPU 使用率时序 |
| **Pod 内存** | `GET /api/monitor/pods/{ns}/{name}/memory` | `sum(container_memory_working_set_bytes{container!=""}) by (pod)` | Pod 内存使用时序 |
| **Pod 网络** | `GET /api/monitor/pods/{ns}/{name}/network` | `sum(rate(container_network_receive_bytes_total[5m])) by (pod)` | Pod 网络 IO 时序 |
| **JVM 堆** | `GET /api/monitor/pods/{ns}/{name}/jvm/heap` | `jvm_memory_used_bytes{area="heap"}` | JVM 堆内存 |
| **JVM 堆外** | `GET /api/monitor/pods/{ns}/{name}/jvm/nonheap` | `jvm_memory_used_bytes{area="nonheap"}` | JVM 非堆内存 |
| **JVM GC** | `GET /api/monitor/pods/{ns}/{name}/jvm/gc` | `rate(jvm_gc_collections_count_total[5m])`, `rate(jvm_gc_collections_elapsed_total[5m])` | GC 次数和耗时 |

### 7.3 后端 PromQL 查询工具类（Java 示例）

```java
// 使用 Spring WebClient 或 RestTemplate 查询 Prometheus
@Component
public class PrometheusClient {
    private final WebClient client;
    
    public PrometheusClient(@Value("${prometheus.host}") String host) {
        this.client = WebClient.create(host + "/api/v1");
    }
    
    // 查询瞬时值
    public Mono<String> query(String promql) {
        return client.get()
            .uri(uri -> uri.path("/query")
                .queryParam("query", promql)
                .build())
            .retrieve()
            .bodyToMono(String.class);
    }
    
    // 查询范围时序
    public Mono<String> queryRange(String promql, long start, long end, String step) {
        return client.get()
            .uri(uri -> uri.path("/query_range")
                .queryParam("query", promql)
                .queryParam("start", start)
                .queryParam("end", end)
                .queryParam("step", step)
                .build())
            .retrieve()
            .bodyToMono(String.class);
    }
    
    // 查询返回格式化结果（使用多个 label 过滤特定 Pod 的 JVM 指标）
    // OTel Java Agent 指标名，自动附加 kubernetes_namespace/kubernetes_pod_name 标签
    public Mono<String> queryJvmHeap(String namespace, String podName) {
        String promql = String.format(
            "jvm_memory_used_bytes{area=\"heap\", kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
        return query(promql);
    }
    
    // JVM GC 次数
    public Mono<String> queryJvmGcCount(String namespace, String podName) {
        String promql = String.format(
            "rate(jvm_gc_collections_count_total{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}[5m])",
            namespace, podName);
        return query(promql);
    }
}
```

### 7.4 平台监控面板数据结构（建议）

```typescript
// 前端 TypeScript 类型定义
interface MonitorMetricPoint {
  timestamp: number;  // Unix timestamp (ms)
  value: number;
}

interface MonitorSeries {
  metric: { [label: string]: string };
  values: MonitorMetricPoint[];
}

interface MonitorResponse {
  status: 'success' | 'error';
  data: {
    resultType: 'vector' | 'matrix' | 'scalar' | 'string';
    result: MonitorSeries[];
  };
}

// Pod 监控面板数据
interface PodMonitorData {
  // 基础指标
  cpuUsage: MonitorSeries[];      // CPU 使用率（% 或 millicores）
  memoryUsage: MonitorSeries[];   // 内存使用（bytes）
  networkRx: MonitorSeries[];     // 网络接收（bytes/s）
  networkTx: MonitorSeries[];     // 网络发送（bytes/s）
  
  // JVM 指标（仅 Java Pod 返回）
  jvmHeapUsed?: MonitorSeries[];      // 堆内存使用
  jvmHeapMax?: MonitorSeries[];        // 堆内存上限
  jvmNonHeapUsed?: MonitorSeries[];    // 非堆内存使用
  jvmGcCount?: MonitorSeries[];        // GC 次数
  jvmGcTime?: MonitorSeries[];         // GC 耗时
}
```

---

## 8. 升级操作

### 8.1 升级版本

#### 步骤 1：拉取新版本镜像

```bash
for img in prometheus:v2.56.0 node-exporter:v1.9.0 kube-state-metrics:v2.16.0 \
           prometheus-operator:v0.78.0 prometheus-config-reloader:v0.78.0; do
  docker pull "quay.io/prometheus/${img}"
done
```

#### 步骤 2：导入 k3s containerd

```bash
for img in prometheus:v2.56.0 node-exporter:v1.9.0 kube-state-metrics:v2.16.0 \
           prometheus-operator:v0.78.0 prometheus-config-reloader:v0.78.0; do
  docker save "quay.io/prometheus/${img}" | \
    docker exec -i devops-k3s ctr -n k8s.io images import -
done
```

#### 步骤 3：更新 values 并 Helm upgrade

```bash
# 修改 values 中的镜像 tag，然后执行 upgrade
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm upgrade prometheus prometheus-community/kube-prometheus-stack --version 63.x \
  -n monitoring \
  -f /tmp/prometheus-values.yaml'
```

---

## 9. 遇到的问题及解决方法

### 问题 1：Prometheus Operator Pod 持续 CrashLoopBackOff

**现象**：`prometheus-operator` Pod 反复重启。

**根因**：缺少 CRD 或 CRD 版本不兼容。首次安装时 Helm 会自动安装 CRD，但如果集群已有旧版 CRD 可能冲突。

**解决**：
```bash
# 检查 CRD 状态
docker exec devops-k3s kubectl get crd | grep monitoring

# 强制删除并重新安装
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm uninstall prometheus -n monitoring'

# 重新安装
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm install prometheus prometheus-community/kube-prometheus-stack --version 62.x \
  -n monitoring -f /tmp/prometheus-values.yaml'
```

### 问题 2：镜像导入后 Pod 仍拉取失败

**现象**：镜像已 `ctr -n k8s.io images import`，但 Pod `ImagePullBackOff`。

**根因**：镜像的 registry 路径不完全匹配。kube-prometheus-stack 的默认镜像路径可能与手动拉取的路径不一致。

**解决**：在 values 中显式指定 `image.repository` 和 `image.tag`（已在 3.5 节 values 中指定）。验证镜像是否已导入：

```bash
docker exec devops-k3s ctr -n k8s.io images ls | grep -E "prometheus|node-exporter|kube-state"
```

### 问题 3：node-exporter 权限不足无法采集指标

**现象**：node-exporter 日志显示 `permission denied` 访问 `/proc` 或 `/sys`。

**根因**：node-exporter 需要访问宿主机 `/proc` 和 `/sys`，但在容器中的 k3s 内运行 node-exporter 需要 `hostPID: true` 和对应 volume mount。

**解决**：kube-prometheus-stack 的 node-exporter DaemonSet 默认已配置 `hostPID: true` 和 `/proc:/host/proc` mount。如果仍有问题，添加以下 values：

```yaml
nodeExporter:
  hostNetwork: true
  hostPID: true
  extraArgs:
    - --path.procfs=/host/proc
    - --path.sysfs=/host/sys
    - --path.rootfs=/host/root
```

### 问题 4：node-exporter TCP 连接数指标为空

**现象**：查询 `node_netstat_Tcp_CurrEstab` 无数据。

**根因**：node-exporter 未开启 `netstat` collector。

**解决**：在 values 中添加 `--collector.netstat` 和 `--collector.tcpstat` 参数（已在 3.5 节 values 中配置）。

### 问题 5：kube-state-metrics 在 ARM64 上启动失败

**现象**：kube-state-metrics Pod `CrashLoopBackOff`，日志显示 `exec format error`。

**根因**：默认镜像不支持 ARM64 架构。

**解决**：在 values 中添加 nodeSelector 强制调度到 ARM64 节点，或使用多架构镜像：

```yaml
kubeStateMetrics:
  image:
    tag: v2.15.0  # 该版本及以上支持多架构（linux/amd64, linux/arm64）
```

### 问题 6：Prometheus 磁盘空间不足

**现象**：Prometheus Pod OOMKilled 或 查询变慢。

**根因**：采集指标太多，超出 PV 容量（20Gi）。

**解决**：
```bash
# 查看当前存储使用
docker exec devops-k3s kubectl -n monitoring exec prometheus-prometheus-0 -- df -h /prometheus

# 缩小 retention
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm upgrade prometheus prometheus-community/kube-prometheus-stack --version 62.x \
  -n monitoring -f /tmp/prometheus-values.yaml \
  --set prometheus.prometheusSpec.retention=7d \
  --set prometheus.prometheusSpec.retentionSize=10GB'

# 或者扩展 PVC
docker exec devops-k3s kubectl -n monitoring edit pvc prometheus-prometheus-db-prometheus-0
# 修改 storage: 20Gi → 50Gi
```

---

## 10. 验证

### 10.1 Pod 状态

```bash
docker exec devops-k3s kubectl -n monitoring get pods
```

期望输出：
```
NAME                                                     READY   STATUS    RESTARTS   AGE
prometheus-prometheus-0                                  2/2     Running   0          5m
prometheus-prometheus-node-exporter-xxxxx                1/1     Running   0          5m
prometheus-operator-xxxxx                                1/1     Running   0          5m
prometheus-kube-state-metrics-xxxxx                     1/1     Running   0          5m
```

### 10.2 Service 状态

```bash
docker exec devops-k3s kubectl -n monitoring get svc
```

期望输出：
```
NAME                                    TYPE        CLUSTER-IP      PORT(S)                         AGE
prometheus-operated                     ClusterIP   None            9090/TCP                        5m
prometheus-prometheus                   NodePort    10.43.xxx.xxx   9090:30090/TCP                  5m
prometheus-prometheus-node-exporter     ClusterIP   10.43.xxx.xxx   9100/TCP                        5m
prometheus-kube-state-metrics           ClusterIP   10.43.xxx.xxx   8080/TCP                        5m
prometheus-operator                     ClusterIP   10.43.xxx.xxx   443/TCP                         5m
```

### 10.3 Prometheus API 验证

```bash
# 验证 Prometheus 自身健康状态
curl -s http://localhost:30090/-/ready

# 查看所有采集目标
curl -s http://localhost:30090/api/v1/targets | jq '.data.activeTargets[].labels.job'

# 验证关键指标
for query in \
  "up" \
  "node_cpu_seconds_total" \
  "node_memory_MemTotal_bytes" \
  "container_cpu_usage_seconds_total" \
  "kube_pod_info"; do
  echo "=== $query ==="
  curl -s "http://localhost:30090/api/v1/query?query=${query}" | jq '.data.result | length'
done
```

### 10.4 Node 指标验证

```bash
# Node CPU 使用率
curl -s 'http://localhost:30090/api/v1/query?query=100%20-%20avg%20by(instance)%20(rate(node_cpu_seconds_total%7Bmode%3D%22idle%22%7D%5B5m%5D))%20*%20100' | jq .

# Node TCP 连接数
curl -s 'http://localhost:30090/api/v1/query?query=node_netstat_Tcp_CurrEstab' | jq .
```

### 10.5 Pod 指标验证

```bash
# Pod CPU 使用
curl -s 'http://localhost:30090/api/v1/query?query=sum(rate(container_cpu_usage_seconds_total%7Bcontainer!%3D%22%22%2Ccontainer!%3D%22POD%22%7D%5B5m%5D))%20by%20(namespace%2C%20pod)' | jq .

# Pod 内存使用
curl -s 'http://localhost:30090/api/v1/query?query=sum(container_memory_working_set_bytes%7Bcontainer!%3D%22%22%2Ccontainer!%3D%22POD%22%7D)%20by%20(namespace%2C%20pod)' | jq .
```

### 10.6 JVM 指标验证（需先部署带 JMX 的 Pod）

```bash
# JVM 堆内存
curl -s 'http://localhost:30090/api/v1/query?query=jvm_memory_used_bytes%7Barea%3D%22heap%22%7D' | jq .

# JVM GC 次数
curl -s 'http://localhost:30090/api/v1/query?query=rate(jvm_gc_collection_count_total%5B5m%5D)' | jq .
```

### 10.7 查看 Helm Release

```bash
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm list -n monitoring'
```

---

## 附录 A：传统 JMX Exporter 配置

> 此方案为备选，适用于已有 JMX Exporter 配置的遗留系统。**新项目请优先使用 OTel Java Agent**（5.1 节）。

### A.1 说明

传统 `prometheus/jmx_exporter` 以 javaagent 方式注入，需要：
1. 拉取 `jmx_prometheus_javaagent.jar` 并挂载到 Pod
2. 手写 JMX MBean → Prometheus 的规则映射
3. 维护 ConfigMap 中的规则配置

### A.2 镜像导入

```bash
docker pull docker.io/bitnami/jmx-exporter:1.0.1
docker save docker.io/bitnami/jmx-exporter:1.0.1 | docker exec -i devops-k3s ctr -n k8s.io images import -
```

### A.3 规则配置 ConfigMap

```bash
cat > /tmp/jmx-config.yaml << 'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: jmx-exporter-config
  namespace: monitoring
data:
  config.yaml: |
    ---
    lowerOutputName: true
    whitelistObjectNames:
      - "java.lang:*"
      - "java.lang:type=Memory,*"
      - "java.lang:type=MemoryPool,*"
      - "java.lang:type=GarbageCollector,*"
      - "java.lang:type=Threading,*"
      - "java.lang:type=ClassLoading,*"
    rules:
      - pattern: 'java.lang:type=Memory'
        name: jvm_memory
        type: GAUGE
        attrNameSnakeCase: true
        labels:
          area: heap
      - pattern: 'java.lang:type=Memory,area=(heap|nonheap)'
        name: jvm_memory_area
        type: GAUGE
        labels:
          area: $1
        attrNameSnakeCase: true
      - pattern: 'java.lang:type=MemoryPool,name=(.*)'
        name: jvm_memory_pool
        type: GAUGE
        labels:
          pool: $1
        attrNameSnakeCase: true
      - pattern: 'java.lang:type=GarbageCollector,name=(.*)'
        name: jvm_gc_collection_time
        type: GAUGE
        labels:
          gc: $1
        attrNameSnakeCase: true
      - pattern: 'java.lang:type=GarbageCollector,name=(.*),key=CollectionCount'
        name: jvm_gc_collection_count
        type: COUNTER
        labels:
          gc: $1
      - pattern: 'java.lang:type=Threading'
        name: jvm_threads
        type: GAUGE
        attrNameSnakeCase: true
      - pattern: 'java.lang:type=ClassLoading'
        name: jvm_classes
        type: GAUGE
EOF

docker cp /tmp/jmx-config.yaml devops-k3s:/tmp/jmx-config.yaml
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl apply -n monitoring -f /tmp/jmx-config.yaml'
```

### A.4 Pod 注入示例

```yaml
spec:
  initContainers:
    - name: jmx-copy
      image: bitnami/jmx-exporter:1.0.1
      command: ["cp", "/opt/bitnami/jmx-exporter/jmx_prometheus_javaagent.jar", "/jmx-agent/"]
      volumeMounts:
        - name: jmx-agent
          mountPath: /jmx-agent
  containers:
    - name: app
      image: your-java-app:latest
      env:
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/jmx-agent/jmx_prometheus_javaagent.jar=9404:/jmx-agent/config.yaml"
      volumeMounts:
        - name: jmx-agent
          mountPath: /jmx-agent
        - name: jmx-config
          mountPath: /jmx-agent/config.yaml
          subPath: config.yaml
    - name: jmx-exporter
      image: bitnami/jmx-exporter:1.0.1
      ports:
        - containerPort: 9404
          name: metrics
  volumes:
    - name: jmx-agent
      emptyDir: {}
    - name: jmx-config
      configMap:
        name: jmx-exporter-config
        defaultMode: 0644
```

## 附录 B：指标名称对照表

OpenTelemetry Java Agent 与 传统 JMX Exporter 的 Prometheus 指标名称对照。

| 含义 | OTel Java Agent（推荐） | 传统 JMX Exporter |
|------|------------------------|-------------------|
| 堆内存已用 | `jvm_memory_used_bytes{area="heap"}` | `jvm_memory_used_bytes{area="heap"}` |
| 堆内存上限 | `jvm_memory_limit_bytes{area="heap"}` | `jvm_memory_max_bytes{area="heap"}` |
| 非堆内存 | `jvm_memory_used_bytes{area="nonheap"}` | `jvm_memory_used_bytes{area="nonheap"}` |
| 内存池 | `jvm_memory_pool_used_bytes` | `jvm_memory_pool_bytes_used` |
| GC 次数 | `jvm_gc_collections_count_total` | `jvm_gc_collection_count_total` |
| GC 耗时 | `jvm_gc_collections_elapsed_total`（counter）<br>`jvm_gc_duration_seconds`（直方图） | `jvm_gc_collection_time_seconds_total`（counter） |
| 当前线程 | `jvm_threads_count` | `jvm_threads_current` |
| 守护线程 | `jvm_threads_daemon_count` | `jvm_threads_daemon` |
| 类加载数 | `jvm_classes_loaded_classes` | `jvm_classes_loaded` |
| 类卸载数 | `jvm_classes_unloaded_total` | `jvm_classes_unloaded_total` |
| CPU 利用率 | `jvm_cpu_recent_utilization_ratio` | 无（需 node-exporter） |

> 提示：如果从 JMX Exporter 迁移到 OTel Java Agent，只需更新 PromQL 中的指标名，其余查询模式（聚合、过滤、rate）完全一致。

## 附录 C：常用命令

```bash
# 查看 Prometheus 日志
kubectl -n monitoring logs -f prometheus-prometheus-0

# 查看 all PVC
kubectl -n monitoring get pvc

# 查看 Helm Values
helm get values prometheus -n monitoring

# 查看镜像是否已导入
ctr -n k8s.io images ls | grep -E "prometheus|node-exporter|kube-state|opentelemetry"

# 手动触发 Prometheus 配置重载
kubectl -n monitoring port-forward svc/prometheus-prometheus 9090:9090 &
curl -X POST http://localhost:9090/-/reload

# 磁盘空间（进入 Prometheus Pod）
kubectl -n monitoring exec prometheus-prometheus-0 -- df -h /prometheus

# 强制删除卡住的 Pod
kubectl -n monitoring delete pod <pod-name> --force --grace-period=0

# 列出 Prometheus 当前采集的所有指标名
curl -s 'http://localhost:30090/api/v1/label/__name__/values' | jq '.data | length'
curl -s 'http://localhost:30090/api/v1/label/__name__/values' | jq '.data[]' | grep -iE "jvm|node_|container_|kube_" | head -50
```