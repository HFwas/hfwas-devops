# 容器管理平台 — 总体设计方案

> 日期：2026-09-10  
> 状态：审查修订  
> 版本：v0.2  
> 关联： [pipeline-core-api.md](../pipeline/pipeline-core-api.md)、[pod-exec-terminal-design.md](../pipeline/pod-exec-terminal-design.md)、[实施计划（Phase 1）](./container-platform-implementation-plan.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-10 | 初版：整体架构、多集群、监控告警、日志、存储网络、镜像仓库、K8s 资源管理、资源拓扑、调试能力 |
| v0.2 | 2026-09-10 | 按审查修订：Phase 1 纳入租户边界；namespaced 资源路径补 namespace；Event 不落全量库；出站 VO；技术栈与仓库对齐；路线图收敛范围 |

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [总体架构](#2-总体架构)
3. [多集群管理](#3-多集群管理)
4. [K8s 资源管理](#4-k8s-资源管理)
5. [资源拓扑与可视化](#5-资源拓扑与可视化)
6. [监控告警](#6-监控告警)
7. [日志管理](#7-日志管理)
8. [存储管理](#8-存储管理)
9. [网络管理](#9-网络管理)
10. [内置镜像仓库](#10-内置镜像仓库)
11. [调试能力](#11-调试能力)
12. [事件管理](#12-事件管理)
13. [多租户与权限](#13-多租户与权限)
14. [API 设计原则](#14-api-设计原则)
15. [技术选型](#15-技术选型)

---

## 1. 背景与目标

### 1.1 需求概述

本平台定位为**企业级容器管理平台**，对标 KubeSphere 核心能力，面向 DevOps 团队提供统一的 Kubernetes 集群管理控制面。核心目标包括：

- **多集群统一纳管**：在单一控制面管理多个 K8s 集群
- **K8s 资源可视化**：Web 界面查看/操作 Pod、Deployment、Service、Namespace 等核心资源
- **资源拓扑与事件可视化**：关联资源依赖关系拓扑、实时事件流
- **监控告警**：集群/节点/工作负载的多维监控与告警
- **日志管理**：Pod 日志实时查看、日志检索与分析
- **存储管理**：StorageClass、PVC/PV 管理，存储容量规划
- **网络管理**：Service、Ingress、NetworkPolicy 可视化配置
- **内置镜像仓库**：集成 Harbor/Distribution 的镜像管理能力
- **调试能力**：Web 终端 exec、ephemeral 容器、Debug Pod 调试

以上为目标能力。各阶段验收范围见 [附录 A](#附录-a实施路线图)，**不得把目标能力当作 Phase 1 范围**。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **控制面轻量** | 平台本身只做编排与展示，不替代 K8s 控制面 |
| **插件化** | 监控、日志、镜像仓库等模块可插拔，按需启用 |
| **租户边界先行** | 集群元数据从第一天带 `tenant_id`，列表/操作/Watch 按 `X-Tenant-Id` 过滤。完整 K8s RBAC 同步可后置，租户隔离不能后置 |
| **安全优先** | Kubeconfig AES-256-GCM 加密存储且永不进 VO / 永不下发浏览器；操作审计；Secret 数据不出站 |
| **熟悉的技术栈** | 后端 Spring Boot 3.4 + Java 21 + fabric8 7.8；前端 Vue 3 + Naive UI + xterm.js + ECharts |

---

## 2. 总体架构

### 2.1 分层架构

```
┌──────────────────────────────────────────────────────┐
│                    前端 SPA (Vue 3 + Naive UI)         │
│  Dashboard │ 资源管理 │ 拓扑 │ 终端 │ 监控 │ 日志     │
└──────────────────────┬───────────────────────────────┘
                       │ REST + 裸 WebSocket
┌──────────────────────▼───────────────────────────────┐
│               API Gateway / Kong                       │
│    认证 · 限流 · 路由 · WebSocket 升级                 │
└──────────────────────┬───────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────┐
│              container-core (Spring 模块)              │
│  ┌──────────────────────────────────────────────────┐ │
│  │               Cluster Management Layer            │ │
│  │  多集群注册 · 租户过滤 · Kubeconfig · Heartbeat    │ │
│  └──────────────────────────────────────────────────┘ │
│  ┌──────────┬───────────┬───────────┬──────────────┐ │
│  │ Resource  │ Monitor   │ Logging   │ Registry    │ │
│  │ Manager   │ Engine    │ Engine    │ Bridge      │ │
│  │ fabric8   │ PromQL    │ Loki / ES │ Harbor API  │ │
│  └──────────┴───────────┴───────────┴──────────────┘ │
│  ┌──────────────────────────────────────────────────┐ │
│  │              Storage & Network Manager            │ │
│  │  StorageClass · PVC · PV · Service · Ingress · NP │ │
│  └──────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────┐ │
│  │               Debug Terminal Engine               │ │
│  │  exec / ephemeral / debug_pod 裸 WebSocket        │ │
│  └──────────────────────────────────────────────────┘ │
└──────────────────────┬───────────────────────────────┘
                       │ fabric8 Kubernetes Client 7.8
┌──────────────────────────────────────────────────────┐
│              Kubernetes 集群 1..N                      │
│  Node │ Pod │ Svc │ PVC │ Prometheus                   │
└──────────────────────────────────────────────────────┘
```

### 2.2 模块划分

| 模块 | 职责 | 关键依赖 |
|------|------|----------|
| **cluster-manager** | 多集群注册、租户归属、Kubeconfig 管理、集群心跳与健康 | fabric8 |
| **resource-manager** | K8s 资源查询与有限变更、Watch（出站 VO，不返回 fabric8 模型） | fabric8 |
| **monitor-engine** | 对接 Prometheus，指标查询与告警管理 | PromQL Client |
| **log-engine** | 对接 Loki/ES，日志采集与检索 | Loki HTTP API / ES RestClient |
| **storage-manager** | StorageClass、PVC/PV、Volume Snapshot 管理 | fabric8 + CSI |
| **network-manager** | Service、Ingress、NetworkPolicy、DNS 管理 | fabric8 |
| **registry-bridge** | 对接 Harbor/Distribution，镜像搜索、Tag 管理 | Harbor REST API |
| **debug-terminal** | 裸 WebSocket 多模式终端（exec/ephemeral/debug_pod） | fabric8 + xterm.js |
| **event-query** | 按资源 UID / Namespace 向 apiserver 查询 Event；可选短窗口内存缓存 | fabric8 |
| **topology-engine** | 资源依赖关系分析，拓扑图数据生成 | 内存图计算 |

### 2.3 与现有系统的集成

- **User Center**：统一 JWT（Keycloak）、`X-Tenant-Id`、现有 RBAC。Phase 1 必须接入。
- **PM**：项目 ↔ Namespace 映射是目标能力，**Phase 1 不建映射表**。资源按集群 + Namespace 过滤。
- **Pipeline**：Phase 1 **与现有单集群 `KubernetesClient`（`pipeline.kubeconfig` 文件）共存**，不替换、不共用同一 Bean。Phase 2 起流水线执行集群改为引用已纳管集群。
- **Kong**：鉴权与路由复用现有基础设施。前端 axios `baseURL` 为 `/api`，后端 Controller 前缀为 `/container`（与 `/pipeline`、`/pm` 一致）。

---

## 3. 多集群管理

### 3.1 集群注册与生命周期

```yaml
# 集群注册所需信息
cluster:
  tenant_id: 1                     # 必填，归属租户；列表与操作按此过滤
  name: "production-beijing"       # 集群标识（租户内唯一）
  alias: "北京生产集群"             # 显示名称
  provider: "ACK"                   # 提供商 (ACK/TKE/self-hosted)
  version: "1.28"                   # 连接成功后回填
  kubeconfig: "<encrypted>"         # AES-256-GCM，仅存库，永不进 ClusterVO
  mode: "proxy"                     # Phase 1 仅 proxy
  labels:
    env: production
    region: beijing
```

**集群连接模式**：

| 模式 | 说明 | 阶段 |
|------|------|------|
| **proxy** | 平台后端通过 fabric8 代理操作，可审计 | Phase 1 唯一模式 |
| **direct** | 前端直连 K8s API Server | **不做**。会把凭证送到浏览器并绕过审计 |

`webkubectl` 见 §11.2，不在 Phase 1，且**禁止**把明文 kubeconfig 注入浏览器。

### 3.2 集群健康与状态

- **心跳**：`@Scheduled` 定时任务，用已缓存的 fabric8 client 调 `/readyz`（或等价 API）+ 节点 Ready 计数；**不** shell 出 `kubectl`。
- **状态枚举**：`Connected` / `Degraded` / `Disconnected` / `Unknown`
- **资源统计看板**：集群级别 CPU/Memory/Storage 总量与使用率（来自 Node allocatable / requests，不依赖 Prometheus）
- **版本与组件**：K8s 版本、kubelet 版本、节点 OS 分布
- **Client 缓存**：`computeIfAbsent` 避免并发双建；`evict` 时 `close()`；进程关闭时关闭全部 client。更新 kubeconfig 必须先 evict。

### 3.3 多集群资源聚合

- **统一搜索**：跨集群搜索 Pod/Deployment/Service（目标能力；Phase 1 仅当前集群）
- **跨集群视图**：按集群、Namespace 维度聚合展示资源总量
- **集群切换**：前端全局集群选择器，切换后所有资源视图联动；选择器只展示当前租户可见集群

### 3.4 Kubeconfig 安全管理

| 措施 | 说明 |
|------|------|
| 加密存储 | 复用与 `CredentialCipher` 相同的 AES-256-GCM；独立 key 配置项 |
| 出站禁止 | `ClusterVO` / 日志 / Watch 消息 **永不**包含 kubeconfig 或解密后的 token |
| 密钥轮换 | 支持定期轮换加密密钥（非 Phase 1） |
| 操作审计 | 注册/更新/删除/连接测试记录操作人、租户、时间 |
| 最小权限 | 建议授予只读 + 必要 exec 的 ServiceAccount，不要求 cluster-admin |

### 3.5 与 pipeline-core 的 client 关系

| 阶段 | 策略 |
|------|------|
| **Phase 1** | `container-core` 自建 `ClusterKubernetesClientFactory`。`pipeline-core` 继续用 `pipeline.kubeconfig` 文件 Bean。两套 client 并存 |
| **Phase 2** | 流水线执行集群改为选择已纳管集群 ID；文件 kubeconfig 仅作开发兜底 |

---

## 4. K8s 资源管理

### 4.1 支持的资源类型（目标能力）

| 类别 | 资源 | 目标操作 | Phase 1 |
|------|------|----------|---------|
| **工作负载** | Deployment、StatefulSet、DaemonSet、Job、CronJob | 查询、扩缩容、滚动更新、回滚、YAML | 仅 Deployment：列表/详情/YAML/扩缩容/重启/删除 |
| **Pod** | Pod | 详情、日志、终端、YAML、Event | 列表/详情/YAML（只读）/日志/事件/删除。**无终端** |
| **服务发现** | Service、Ingress、EndpointSlice | 查询与配置 | 仅 Service：列表/详情 |
| **配置** | ConfigMap、Secret | 查询与 YAML | 不做。Secret 数据永远不出站 |
| **存储 / 网络 / 权限 / CRD** | 见表后阶段 | — | 不做 |

namespaced 资源的主键是 **`clusterId + namespace + name`**。列表可用 query `namespace`；详情、YAML、日志、删除、Watch 过滤必须带 namespace。

### 4.2 统一 UI 模式

每个资源类型遵循统一的界面模式：

1. **列表页**：搜索/过滤/排序/分页 + 状态着色（批量写操作后置）
2. **详情页**：概览 Tab + YAML Tab + 事件 Tab + 关联资源 Tab
3. **创建/编辑**：表单 + YAML（**Phase 2+**）
4. **资源 YAML**：Monaco Editor；Phase 1 只读。Secret 类资源即使后期开放也只出 metadata，不出 `data`

### 4.3 Pod 详情页

| Tab | 内容 | 阶段 |
|-----|------|------|
| **概览** | Pod 状态、Node IP、Pod IP、QoS、Owner、Container 列表与状态 | Phase 1 |
| **YAML** | Pod Spec（只读 VO / YAML 字符串，脱敏） | Phase 1 |
| **日志** | 多容器选择、实时 Tail、下载 | Phase 1 |
| **事件** | 按 involvedObject UID 查 apiserver | Phase 1 |
| **终端** | exec / ephemeral / debug_pod | **Phase 2**，Phase 1 页面不放入口 |
| **监控** | CPU/Memory/Network 时序图 | Phase 3 |
| **拓扑** | 上游链路 | Phase 2 |

### 4.4 WebSocket 实时推送

复用 pipeline 终端的**裸 WebSocket**（非 STOMP）。握手从 `Sec-WebSocket-Protocol` 取 JWT，并校验集群对当前租户可见。

```
Client → WS /ws/container/watch/{clusterId}?resources=Pod,Deployment,Service&namespace=default
Server → {"type":"ADDED|MODIFIED|DELETED","resource":"Pod","object":{/* ResourceWatchVO */}}
```

- `object` 为摘要 VO（kind / namespace / name / status / 关键字段），**不是** fabric8 全量对象
- 前端连接时注册 Watch，断连关闭 Informer 注册
- 按 Namespace 过滤；禁止无 namespace 的全集群 Watch（Phase 1）
- 批量变更合并（Debounce 300ms）
- 连接数按用户/租户设上限

---

## 5. 资源拓扑与可视化

Phase 2 能力，Phase 1 不实施。

### 5.1 拓扑数据模型

以 Pod 为中心构建资源依赖图，展示上下游关系：

```
Ingress ──→ Service ──→ Pod ──→ PVC
                            │
                    ┌───────┴───────┐
               Deployment      StatefulSet
                    │
              ReplicaSet

  Pod ──→ 所属 Node
  Pod ──→ 关联 ConfigMap／Secret
  Pod ──→ 所属 Owner (Deployment/StatefulSet/Job)
  Service ──→ 选择器匹配 Pod
  Ingress ──→ 路由到 Service
  NetworkPolicy ──→ 作用于 Pod
```

### 5.2 拓扑图交互

- **渲染引擎**：AntV G6（优先，与 Naive UI 并存）或 D3.js
- **交互操作**：点击跳转详情；右键日志/终端/YAML（终端仅 Phase 2+）
- **状态着色**：绿/黄/红/灰
- **指标叠加**：可选 CPU/Memory 百分比（依赖 Phase 3 监控）

### 5.3 应用级拓扑视图

将 Deployment + Service + Ingress + ConfigMap + PVC 聚合为**应用视图**，便于整体排查。

### 5.4 时序可视化

- **Pod 启动时间线**：Pending → ContainerCreating → Running 各阶段耗时
- **资源变更时间线**：Deployment 滚动更新历史
- **事件时间线**：按时间轴排列，Warning 高亮

---

## 6. 监控告警

Phase 3 能力。平台**不部署** Prometheus，只做 PromQL / Alertmanager 消费层。

### 6.1 架构

```
Node / Pod / kube-state-metrics ──► Prometheus ──► 平台 Monitor Engine
                                      │
                                      ▼
                                 Alertmanager ──► Email / Webhook / 飞书
```

可选 Grafana 嵌入；可选 VictoriaMetrics 仅边缘场景，不作为默认路径。

### 6.2 指标采集维度

| 维度 | 关键指标 | PromQL 示例 |
|------|----------|-------------|
| **集群** | CPU/Memory/Storage 总量与使用率、Node Ready 数 | `sum(kube_node_status_condition{condition="Ready",status="true"})` |
| **Node** | CPU/Mem/Disk/Network I/O、Load、Pod 密度 | `1 - avg(rate(node_cpu_seconds_total{mode="idle"}[5m]))` |
| **工作负载** | CPU/Mem 使用、副本数与可用数、重启次数 | `sum(rate(container_cpu_usage_seconds_total{container!=""}[5m])) by (pod)` |
| **Pod** | CPU Throttling、OOM、Container 重启、网络丢包 | `kube_pod_container_status_restarts_total` |

### 6.3 监控面板

集群概览、Node 列表、工作负载与 Pod 时序、自定义 PromQL 面板。

### 6.4 告警管理

内置规则、规则 CRUD、按严重级别/集群/Namespace 路由、静默、历史、通知渠道（Email / Webhook / 飞书 / 钉钉 / 企业微信）。

### 6.5 告警规则示例

`kube_pod_status_phase` 的 phase **没有** `CrashLoopBackOff`（只有 Pending / Running / Succeeded / Failed / Unknown）。CrashLoop 用 waiting reason：

```yaml
- name: PodCrashLoopBackOff
  expr: kube_pod_container_status_waiting_reason{reason="CrashLoopBackOff"} > 0
  for: 1m
  severity: critical
  labels:
    category: workload
  annotations:
    summary: "Pod {{ $labels.pod }} 容器 {{ $labels.container }} 处于 CrashLoopBackOff"

- name: NodeNotReady
  expr: kube_node_status_condition{condition="Ready",status="true"} == 0
  for: 5m
  severity: critical

- name: PVCUsageHigh
  expr: (kubelet_volume_stats_used_bytes / kubelet_volume_stats_capacity_bytes) * 100 > 80
  for: 10m
  severity: warning
```

---

## 7. 日志管理

### 7.1 架构

```
Pod ──stdout──► Node ──fluentbit──► Loki / ES ──► 平台 Log Engine ──► 前端日志面板
```

- **采集层**：Fluent Bit / Vector DaemonSet（集群侧，非本平台进程）
- **存储层**：Loki（推荐）或 Elasticsearch
- **查询层**：平台只做 HTTP 查询。Phase 1 **实时 Tail 走 kube API**（`watchLog`），不依赖 Loki

### 7.2 功能特性

| 功能 | 说明 | 阶段 |
|------|------|------|
| **实时日志** | Pod 多容器 Tail（裸 WebSocket） | Phase 1 |
| **历史查询** | 时间范围、关键字、Label / LogQL | Phase 3（需 Loki） |
| **日志下载** | 当前 Tail / 查询结果 | Phase 1（当前缓冲） |
| **多容器选择** | 分别或聚合 | Phase 1 |
| **日志高亮 / 仪表盘 / 转告警** | — | Phase 3+ |

### 7.3 日志查询 API

后端无 `/api` 前缀。历史查询（Phase 3）：

```http
GET /container/clusters/{clusterId}/logs/query
  ?namespace=default
  &pod=demo-app-7d8f9c-abc01
  &container=main
  &query=ERROR
  &start=2026-09-10T00:00:00Z
  &end=2026-09-10T23:59:59Z
  &limit=100
  &order=desc
```

Phase 1 实时 Tail：

```
WS /ws/container/logs/{clusterId}?namespace=default&pod=...&container=...
```

握手鉴权同 §4.4，并校验集群可见 + namespace + pod 存在。

### 7.4 日志存储策略

由 Loki/ES 自身 retention 负责，平台不落日志到 SQLite。热/温/冷分层是集群侧建议，不是本模块表结构。

---

## 8. 存储管理

Phase 4 能力。

### 8.1 能力范围

| 功能 | 说明 |
|------|------|
| **StorageClass 管理** | 列表、详情、默认 StorageClass 标记、CSI 扩展参数 |
| **PVC 管理** | 创建/删除、容量与访问模式、状态监控 |
| **PV 管理** | 列表、Reclaim Policy、状态 |
| **Volume Snapshot** | 创建/删除、基于快照恢复 PVC |
| **容量规划** | 按 StorageClass 聚合已用/可用，使用率趋势 |
| **存储拓扑** | PVC → PV → StorageClass |

### 8.2 PVC 创建 UI

```
创建 PVC:
  名称 / Namespace / StorageClass / 访问模式 (RWO|RWX|ROX) / 容量
  容量分析: 该 StorageClass 已用 45/100Gi
```

### 8.3 存储监控

- 按 StorageClass / Namespace 聚合使用量
- PVC 使用率阈值告警（> 80% Warning, > 95% Critical）
- PV 状态分布（Available / Bound / Released / Failed）

---

## 9. 网络管理

Service **只读**在 Phase 1。Ingress / NetworkPolicy 可视化为 Phase 3。端口转发为 Phase 2。

### 9.1 能力范围

| 功能 | 说明 | 阶段 |
|------|------|------|
| **Service** | 列表/详情；后续 ClusterIP/NodePort/LB 配置 | Phase 1 只读，写操作 Phase 3 |
| **Ingress** | Host/Path、TLS、Annotation | Phase 3 |
| **NetworkPolicy** | 可视化 Ingress/Egress、PodSelector、CIDR、Port | Phase 3 |
| **Service 拓扑** | Service → Endpoint → Pod | Phase 2 |

### 9.2 NetworkPolicy 可视化编辑器

策略类型（Ingress / Egress / 双向）+ Pod 选择器 + 来源（PodSelector / IPBlock / Namespace）+ 端口；可切 YAML。

### 9.3 端口转发 (Port-Forward)

- **仅** WebSocket 代理到目标 Pod 端口，流量不出集群 Node 网络
- **禁止**为调试创建临时 NodePort / LoadBalancer
- 与终端一同在 Phase 2 交付

---

## 10. 镜像仓库桥

Phase 4 能力。通过 Harbor / Registry V2 Adapter 对接。

**不**与控制台 `artifact`（制品仓库）产品位合并：`artifact` 占位保留；本模块是已纳管仓库的浏览与部署入口。

### 10.1 架构

```
Registry Bridge ── Harbor Adapter / Docker Registry Adapter
        │ Docker Registry HTTP API V2
        ▼
Harbor / Docker Registry（外部或内置）
```

### 10.2 功能特性

| 功能 | 说明 |
|------|------|
| **项目管理** | 对应 Harbor Project 或 Registry Namespace |
| **镜像浏览 / 搜索** | 项目 / 仓库 / Tag，分页 |
| **Tag 管理** | 列表、删除、保留策略 |
| **镜像构建** | 与 Pipeline 集成，构建后推送 |
| **镜像部署** | 选 Tag → 填 Deployment 参数 → 创建（依赖 Phase 2 YAML 应用） |
| **漏洞扫描** | 展示 Harbor Trivy 结果 |
| **代理缓存 / Webhook** | 可选 |

### 10.3 内置 vs 外部

| 方式 | 推荐场景 |
|------|----------|
| **对接 Harbor** | 已有 Harbor |
| **对接 Docker Registry** | 轻量 |
| **内置 MinIO + Distribution** | 离线/边缘，非默认 |

---

## 11. 调试能力

Phase 2 能力（终端 / 端口转发 / 文件拷贝）。复用 [pod-exec-terminal-design.md](../pipeline/pod-exec-terminal-design.md) 的模式降级，但走 **已纳管集群的 client**，不再读 `pipeline.kubeconfig`（与 §3.5 的 Phase 2 切换一致）。

### 11.1 终端调试模式

```
WebSocket ──→ 握手鉴权（JWT + 租户 + 集群可见）──→ 模式判定
                    exec 可用 → fabric8 ExecWatch
                    exec 不可用且 Pod 仍在 → ephemeral
                    Pod 已删且 PVC 残留 → Debug Pod
                    Pod 已删且 emptyDir → 明确不可达
```

### 11.2 Web kubectl

- 浏览器只出命令行 UI，**命令在服务端执行**
- **禁止**把 kubeconfig 下发到浏览器或「自动注入」到前端
- 每条命令记审计（用户、租户、集群、原文、结果）
- 默认只允许只读 verb；写操作需显式角色（Phase 5 角色模型）

### 11.3 调试辅助功能

| 功能 | 说明 |
|------|------|
| **Pod 诊断** | Event、Recent Logs、Resource Usage、Probe |
| **端口转发** | 见 §9.3，仅 WS 代理 |
| **Copy 文件** | kubectl cp 的 Web 等价，限制大小与路径 |
| **Node 调试** | 特权 Debug Pod，需独立高权限角色 |

### 11.4 Debug Pod 规范

镜像**必须可配置**，默认指向内网仓库，不写死公共 `nicolaka/netshoot`：

```yaml
# application.yml
container:
  debug:
    image: ${CONTAINER_DEBUG_IMAGE:registry.internal/debug/netshoot:latest}
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: debug-{user}-{random}
  namespace: {target-ns}
  labels:
    app.kubernetes.io/managed-by: hfwas-container
spec:
  containers:
  - name: debug
    image: "{container.debug.image}"
    command: ["sleep", "infinity"]
    volumeMounts:
    - mountPath: /mnt/target
      name: target-workspace
  restartPolicy: Never
  activeDeadlineSeconds: 1800
  nodeName: {target-node}
  volumes:
  - name: target-workspace
    persistentVolumeClaim:
      claimName: {target-pvc}   # 仅 RWO 且原 Pod 已释放时
```

---

## 12. 事件管理

### 12.1 采集策略（修订）

**禁止**对全集群 Event 起长期 `SharedInformer` 并写入 SQLite。Event 量大、生命周期短，本地库会先被打满。

| 路径 | 做法 |
|------|------|
| **资源详情事件 Tab** | `client.v1().events().inNamespace(ns).withField("involvedObject.uid", uid)` 即时查询 |
| **列表页 Warning 提示** | 同上，按当前页资源 UID 批量查 |
| **实时流（可选）** | 仅当前 Namespace 的短生命周期 Watch；进程内 ring buffer（例如 500 条），断连即丢 |
| **持久化** | Phase 1 **无** `container_event` 表。告警历史在 Phase 3 随 Alertmanager |

Watch 断连用 Exponential Backoff 重连；集群删除时关闭对应 Watch。

### 12.2 事件可视化

| 视图 | 说明 | 阶段 |
|------|------|------|
| **资源事件** | Pod / Deployment 详情 Tab | Phase 1 |
| **集群事件流** | 当前 Namespace 实时推送，Warning 高亮 | Phase 1 可选 |
| **事件聚合 / 时间线** | 按 Reason / Kind 聚合 | Phase 2+ |

### 12.3 事件告警联动

Phase 3：Warning 关联告警、事件风暴收敛。不在 Phase 1 做。

---

## 13. 多租户与权限

### 13.1 Phase 1 最小权限（必须落地）

复用现有 `TenantContextFilter` + `X-Tenant-Id` + JWT：

- `cluster_info.tenant_id` **必填**，注册时写入当前租户
- 列表 / 详情 / 更新 / 删除 / 连接测试 / stats：**按租户过滤**，跨租户 404
- 资源 API 与 Watch / Tail：先解析集群，校验 `cluster.tenantId == currentTenantId`
- Platform Admin 跨租户查看可后置；Phase 1 不做「全域集群」
- 项目 ↔ Namespace、角色同步到 K8s RBAC：**不做**

```
Platform ── 租户 (Tenant) ── 已纳管集群 (cluster_info.tenant_id)
```

### 13.2 目标租户模型（Phase 5 补齐）

```
Platform ── 租户 ── 项目 (Project) ── Namespace
                └── 集群（多对多授权）
```

- **租户 ↔ 集群**：多对多授权（Phase 1 先一对多：一集群一租户）
- **项目 ↔ Namespace**：一对一或一对多
- **K8s RBAC 透传**：平台角色同步为 RoleBinding

### 13.3 目标角色模型

| 角色 | 权限范围 | 阶段 |
|------|----------|------|
| **Platform Admin** | 全域 | Phase 5 |
| **Tenant Admin** | 租户内集群与配额 | Phase 5 |
| **Project Admin** | 项目内资源写 | Phase 5 |
| **Operator** | 查看 + exec / 日志 / 扩缩容 | Phase 5（Phase 1 登录用户在本租户内即可操作有限变更） |
| **Viewer** | 只读 | Phase 5 |

### 13.4 操作审计

Phase 1 至少记录：集群注册/更新/删除/测试、Pod 删除、Deployment 扩缩容/重启。字段：timestamp、user、tenant、cluster、namespace、resource、name、action、result。完整 Diff 与 exec 审计在 Phase 5 补齐。

---

## 14. API 设计原则

### 14.1 接口规范

与后端现有规范一致，返回 `BaseResult<T>`，分页为 MyBatis-Plus **`IPage<T>`**（前端 `PageResult<T>` 与之对齐）。**后端不定义 `PageResult`。**

```json
{
  "code": 0,
  "msg": "成功",
  "data": {},
  "requestId": "req-xxx"
}
```

### 14.2 REST 路由

后端 Controller **无** `/api` 前缀（由前端 axios / nginx 加 `/api` 再剥掉），与 `/pipeline`、`/pm` 一致。

```
/container/clusters
/container/clusters/{clusterId}
/container/clusters/{clusterId}/stats
/container/clusters/{clusterId}/namespaces
/container/clusters/{clusterId}/pods?namespace={ns}                          # 列表
/container/clusters/{clusterId}/namespaces/{ns}/pods/{name}                 # 详情
/container/clusters/{clusterId}/namespaces/{ns}/pods/{name}/yaml
/container/clusters/{clusterId}/namespaces/{ns}/pods/{name}/logs            # 历史快照（非 WS）
/container/clusters/{clusterId}/namespaces/{ns}/events?uid={involvedUid}
/container/clusters/{clusterId}/namespaces/{ns}/deployments/{name}
/container/clusters/{clusterId}/namespaces/{ns}/services/{name}
```

后续模块沿用同一前缀：`/container/clusters/{clusterId}/...`。不要再引入无 clusterId 的扁平 `/container/pods`。

### 14.3 WebSocket 路由

```
WS /ws/container/watch/{clusterId}?resources=Pod,Deployment,Service&namespace={ns}
WS /ws/container/logs/{clusterId}?namespace={ns}&pod={pod}&container={container}
WS /ws/container/events/{clusterId}?namespace={ns}          # 可选
WS /ws/container/terminal/{clusterId}?namespace={ns}&pod=…  # Phase 2
```

- Security：`/container/**` → `authenticated()`（显式写出，**不是**匿名白名单）
- `/ws/container/**` → `permitAll()`，鉴权在 `ContainerWsAuthInterceptor`（复用 `PodExecAuthHandshakeInterceptor` 的 `Sec-WebSocket-Protocol` JWT），握手失败关闭连接
- 拦截器必须校验：JWT 有效、租户、`clusterId` 可见、namespaced 参数齐全

### 14.4 出站模型

| 规则 | 说明 |
|------|------|
| 只出 VO | Controller / Watch **不**返回 fabric8 `Pod` / `Deployment` 等模型 |
| kubeconfig | 不出现在任何 VO、日志、异常堆栈 |
| Secret | `data` / `stringData` 不出站；列表只出 name / type / 数据键名 |
| YAML | 服务端序列化后的字符串；可对 Secret 做脱敏 |

---

## 15. 技术选型

### 15.1 后端

| 组件 | 技术 | 说明 |
|------|------|------|
| **框架** | Spring Boot 3.4 + Java 21 | 与仓库一致 |
| **K8s 客户端** | fabric8 Kubernetes Client **7.8.0**（parent BOM，覆盖 Boot 自带 6.9.2） | 不要按 6.x API 习惯写 |
| **WebSocket** | Spring **裸** WebSocket（`AbstractWebSocketHandler`） | 与 pipeline 终端一致；**不用 STOMP** |
| **数据库** | **SQLite**（当前运行时）+ `db/container-schema.sql` + `SqliteSchemaInitializer` | PostgreSQL 单独立项，不是 Phase 1 前提 |
| **缓存** | Phase 1 进程内 ConcurrentHashMap | Redis 不作为 Phase 1 依赖 |
| **监控 / 日志 / 仓库** | PromQL / Loki / Harbor Adapter | 后阶段按需 |

### 15.2 前端

| 组件 | 技术 | 说明 |
|------|------|------|
| **框架** | Vue 3 + TypeScript | 与仓库一致 |
| **UI 组件库** | **Naive UI** | 不要引入 Element Plus / Ant Design Vue |
| **拓扑图** | AntV G6 | Phase 2 |
| **时序图** | ECharts | Phase 3 |
| **终端** | `@xterm/xterm` | Phase 2，复用 pipeline 终端组件模式 |
| **YAML 编辑** | Monaco Editor | Phase 1 只读查看即可 |
| **实时数据** | 浏览器原生 `WebSocket` | **不用 STOMP.js** |
| **状态管理** | Pinia composition store | 集群选择 |

### 15.3 外部组件

| 组件 | 角色 | 阶段 |
|------|------|------|
| **Prometheus + kube-state-metrics + node-exporter** | 指标 | Phase 3，集群侧部署 |
| **Alertmanager** | 告警路由 | Phase 3 |
| **Loki + Promtail / Fluent Bit** | 历史日志 | Phase 3 |
| **Harbor** | 镜像仓库 | Phase 4，可选 |
| **Grafana** | 嵌入面板 | 可选 |

### 15.4 产品入口

控制台 **新增** `key: 'container'`（容器管理），**保留** `resource`（资源编排）与 `artifact`（制品仓库）的 comingSoon 占位，不替换。

---

## 附录 A：实施路线图

| 阶段 | 内容 | 预估周期 |
|------|------|----------|
| **Phase 1** | 多集群纳管（含 `tenant_id`）+ 只读资源（NS/Pod/Deploy/Svc）+ 有限变更（删 Pod、Deploy 扩缩容/重启）+ Pod 详情/YAML/日志/事件 + 模块接线（server 依赖、schema、心跳） | 4 周 |
| **Phase 2** | 资源拓扑 + Web 终端 + 端口转发（仅 WS）+ YAML 应用 + 流水线改引用纳管集群 | 3 周 |
| **Phase 3** | 监控面板 + 告警 + Loki 历史日志 + NetworkPolicy / Ingress | 4 周 |
| **Phase 4** | 存储管理 + 镜像仓库 Adapter | 3 周 |
| **Phase 5** | 项目↔Namespace、角色同步到 K8s RBAC、完整审计 Diff、性能优化 | 2 周 |

Phase 1 **验收不包含**：完整 CRUD / 创建资源 / 终端 / 拓扑 / 监控 / 镜像仓库 / 全量 Event 落库 / 多租户角色矩阵。

## 附录 B：与现有子系统的集成清单

| 现有子系统 | 集成点 | 阶段 |
|------------|--------|------|
| **User Center** | JWT、`X-Tenant-Id`、租户过滤 | Phase 1 |
| **PM** | 项目 ↔ Namespace 映射、项目级配额 | Phase 5 |
| **Pipeline** | Phase 1 共存；Phase 2 执行集群改引用 `clusterId`；终端入口复用握手方式 | Phase 1–2 |
| **Kong / Vite** | `/api` 剥前缀；`/ws/container/**` 与现有 `/ws/exec/**` 一样升级 | Phase 1 |
| **控制台产品** | 新增 `container`，保留 `resource` | Phase 1 |

---

*本文档为容器管理平台总体设计方案 v0.2。Phase 1 细节见实施计划。*