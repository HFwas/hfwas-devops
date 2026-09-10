# 容器管理平台监控接入 — 设计方案

> 日期：2026-09-10  
> 状态：待实施  
> 版本：v0.1  
> 关联：[container-platform-design.md](./container-platform-design.md)、[prometheus-deployment.md](./prometheus-deployment.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-10 | 初版：监控接入架构、API、页面设计 |

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [现有架构分析](#2-现有架构分析)
3. [设计方案](#3-设计方案)
4. [后端接口定义](#4-后端接口定义)
5. [前端页面设计](#5-前端页面设计)
6. [Java JVM 监控面板](#6-java-jvm-监控面板)
7. [图表组件设计](#7-图表组件设计)
8. [边界情况与错误处理](#8-边界情况与错误处理)
9. [实施计划](#9-实施计划)
10. [新增文件清单](#10-新增文件清单)

---

## 1. 背景与目标

### 1.1 需求

按照 `prometheus-deployment.md` 在集群侧部署 Prometheus + node-exporter + kube-state-metrics + OTel Java Agent 后，容器管理平台需要作为 PromQL 消费层提供可视化监控能力。

需要覆盖的监控维度：

| 维度 | 采集方式 | 展示场景 |
|------|----------|----------|
| **Node CPU/内存/网络 IO/连接数** | node-exporter | Node 详情页 |
| **Pod CPU/内存/网络 IO** | kubelet cAdvisor | Pod 详情页 |
| **JVM 堆内/堆外/GC/线程** | OTel Java Agent | Java Pod 详情页 |
| **集群概览** | 聚合以上 | 集群详情页 |

### 1.2 目标

- 在现有 Pod / Node / Cluster 详情页的 Tab 中新增"监控"面板
- 趋势图使用 ECharts 渲染，支持 1h/6h/24h/7d 时间范围切换
- 后端 Java 服务代理 Prometheus API，前端不直连 Prometheus
- JVM 监控仅在检测到 OTel Java Agent 指标时自动展示

### 1.3 约束

- 平台 **不部署** Prometheus，只做 PromQL 消费层（验证设计已有约定，见 `container-platform-design.md §6.1`）
- 单节点集群，Prometheus 地址通过集群 labels 配置
- 遵循现有 `BaseResult<T>` API 模式和 Naive UI 组件风格

---

## 2. 现有架构分析

```
容器管理平台（Docker Compose）
┌───────────────────────────────────────────────┐
│  frontend (Vue 3 + TS + Naive UI)            │
│    └─ axios → /api → kong → backend:8089     │
│  backend (Spring Boot)                       │
│    └─ container-core                          │
│      ├─ ClusterController / NodeController    │
│      │  PodController / DeploymentController  │
│      └─ ResourceService (K8s API via fabric8) │
└───────┬───────────────────────────────────────┘
        │ HTTP (NodePort 30090)
        ▼
k3s 集群侧 Prometheus (kube-prometheus-stack)
  ├─ node-exporter:9100
  ├─ kube-state-metrics:8080
  └─ kubelet cAdvisor (内置于 kubelet)
```

### 2.1 现有后端模式

- 路径前缀：`/container/clusters/{clusterId}`
- 返回包装：`BaseResult<T>`（code / msg / data）
- 集群认证：`ClusterEntity` 存加密 kubeconfig，依赖 `ClusterKubernetesClientFactory`
- **尚无 PromQL 查询能力**

### 2.2 现有前端模式

- 模块化结构：`src/modules/container/{api,views,components,router,types}`
- HTTP 请求：`src/shared/api/request.ts`（封装 axios，自动注入 auth token + tenantId）
- UI：Naive UI（`n-card`, `n-tabs`, `n-descriptions`, `n-data-table`, `n-statistic`, `n-tag`）
- 图标：`@lucide/vue`
- **尚无图表依赖**（需要加 echarts + vue-echarts）
- 页面 Tab 模式：PodDetailView 使用 `<n-tabs type="line">` 切换子面板

### 2.3 前端页面路由一览

| 路由 | 页面 | 需要加监控 Tab |
|------|------|---------------|
| `clusters/:id` | ClusterDetailView | ✅ |
| `clusters/:clusterId/nodes/:name` | NodeDetailView | ✅ |
| `clusters/:clusterId/pods/:namespace/:name` | PodDetailView | ✅ |

---

## 3. 设计方案

### 3.1 架构

```
前端 Vue 组件（监控 Tab）
  │ 调用 api/monitor.ts
  ▼
backend MonitorController
  │ 组装 PromQL + 调用 PrometheusClient
  ▼
PrometheusClient (WebClient)
  │ GET /api/v1/query_range → 返回 Prometheus 原始 JSON
  ▼
MonitorService (解析、格式化)
  │ 转换 → MonitorSeriesVO[]
  ▼
前端 ECharts 渲染
```

### 3.2 集群关联 Prometheus 地址

每个纳管的集群在其 `labels` 字段（JSON 字符串，存于 `cluster_info` 表）中标注 Prometheus 地址：

```json
{
  "prometheusUrl": "http://<k3s-node-ip>:30090",
  "prometheusHeaders": ""
}
```

> 后续可通过集群详情页编辑 labels 或单独的表单配置，Phase 1 直接用 JSON 写死。

后端解析：

```java
// ClusterService 中提取
public String getPrometheusUrl(Long clusterId) {
    ClusterEntity entity = getById(clusterId);
    Map<String, String> labels = parseLabels(entity.getLabels());
    return labels.getOrDefault("prometheusUrl", "");
}
```

### 3.3 后端核心组件

在 `container-core` 中新增 `prometheus` 包：

```
container-core/src/main/java/com/hfwas/devops/container/
  ├── service/prometheus/
  │   ├── PrometheusClient.java         # Prometheus HTTP API 调用
  │   ├── PrometheusQueryBuilder.java   # PromQL 构造
  │   └── MonitorService.java            # 业务组装
  ├── controller/MonitorController.java  # REST 端点
  └── dto/
      ├── MonitorSeriesVO.java          # 时序数据响应
      ├── MonitorPointVO.java           # 单个时序点
      └── MonitorOverviewVO.java        # 集群概览聚合
```

---

## 4. 后端接口定义

### 4.1 通用查询参数

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `range` | string | `1h` | 时间范围：`1h` / `6h` / `24h` / `7d` |
| `step` | string | 自动 | 采样步长（根据 range 推算：1h→15s, 6h→1m, 24h→5m, 7d→30m） |
| `container` | string | — | 容器名（多容器 Pod 指定） |

### 4.2 响应 VO

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorSeriesVO {
    private Map<String, String> labels;      // 维度标签 (pod, namespace, instance...)
    private List<MonitorPointVO> points;     // 时序点列表
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorPointVO {
    private long timestamp;  // Unix 秒级时间戳
    private double value;
}

@Data
public class MonitorOverviewVO {
    private double cpuUsagePercent;
    private double memoryUsagePercent;
    private int nodeTotal;
    private int nodeReady;
    private int podTotal;
    private int podRunning;
    private long diskReadBytesPerSec;
    private long diskWriteBytesPerSec;
}
```

### 4.3 Node 监控端点

#### 获取 Node CPU 使用率

```
GET /container/clusters/{clusterId}/monitor/nodes/{name}/cpu
  ?range=1h&step=15s
```

**PromQL**:
```promql
100 - avg by(instance) (
  rate(node_cpu_seconds_total{mode="idle", instance=~".*${nodeName}.*"}[5m])
) * 100
```

**响应**:
```json
{
  "code": 200,
  "data": [{
    "labels": { "instance": "k3s:9100" },
    "points": [
      { "timestamp": 1725900000, "value": 45.2 },
      { "timestamp": 1725900015, "value": 46.1 }
    ]
  }]
}
```

#### 获取 Node 内存使用率

```
GET /container/clusters/{clusterId}/monitor/nodes/{name}/memory
```

**PromQL**:
```promql
(1 - node_memory_MemAvailable_bytes{instance=~".*${nodeName}.*"}
      / node_memory_MemTotal_bytes{instance=~".*${nodeName}.*"}) * 100
```

#### 获取 Node 网络 IO

```
GET /container/clusters/{clusterId}/monitor/nodes/{name}/network
```

**PromQL（接收）**:
```promql
rate(node_network_receive_bytes_total{instance=~".*${nodeName}.*", device!="lo"}[5m])
```

**PromQL（发送）**:
```promql
rate(node_network_transmit_bytes_total{instance=~".*${nodeName}.*", device!="lo"}[5m])
```

**响应**（多条时返回多个 series）:
```json
{
  "code": 200,
  "data": [
    {
      "labels": { "instance": "k3s:9100", "device": "eth0", "direction": "receive" },
      "points": [...]
    },
    {
      "labels": { "instance": "k3s:9100", "device": "eth0", "direction": "transmit" },
      "points": [...]
    }
  ]
}
```

#### 获取 Node TCP 连接数

```
GET /container/clusters/{clusterId}/monitor/nodes/{name}/connections
```

**PromQL**:
```promql
node_netstat_Tcp_CurrEstab{instance=~".*${nodeName}.*"}
```

#### 获取 Node 磁盘 IO

```
GET /container/clusters/{clusterId}/monitor/nodes/{name}/disk
```

**PromQL（读）**:
```promql
rate(node_disk_read_bytes_total{instance=~".*${nodeName}.*"}[5m])
```

**PromQL（写）**:
```promql
rate(node_disk_written_bytes_total{instance=~".*${nodeName}.*"}[5m])
```

### 4.4 Pod 监控端点

#### 获取 Pod CPU 使用

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/cpu
```

**PromQL**:
```promql
sum(rate(container_cpu_usage_seconds_total{
  namespace="${ns}",
  pod="${name}",
  container!="",
  container!="POD"
}[5m])) by (pod, container) * 1000
```

> 单位: millicores

#### 获取 Pod 内存使用

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/memory
```

**PromQL**:
```promql
sum(container_memory_working_set_bytes{
  namespace="${ns}",
  pod="${name}",
  container!="",
  container!="POD"
}) by (pod, container)
```

> 单位: bytes

#### 获取 Pod 网络 IO

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/network
```

**PromQL（接收）**:
```promql
sum(rate(container_network_receive_bytes_total{namespace="${ns}", pod="${name}"}[5m])) by (pod)
```

**PromQL（发送）**:
```promql
sum(rate(container_network_transmit_bytes_total{namespace="${ns}", pod="${name}"}[5m])) by (pod)
```

#### 获取 Pod OOM / 重启事件

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/events
```

**PromQL**:
```promql
kube_pod_container_status_restarts_total{namespace="${ns}", pod="${name}"}
```

### 4.5 JVM 监控端点

> 仅对注入 OTel Java Agent 的 Pod 返回数据，无数据时前端隐藏 JVM 面板。

#### 获取 JVM 堆内存

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/heap
```

**PromQL**:
```promql
# 已用
jvm_memory_used_bytes{area="heap", kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}
# 上限
jvm_memory_limit_bytes{area="heap", kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}
```

#### 获取 JVM 非堆内存

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/nonheap
```

**PromQL**:
```promql
jvm_memory_used_bytes{area="nonheap", kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}
```

#### 获取 JVM GC 统计

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/gc
```

**PromQL**:
```promql
# GC 次数
rate(jvm_gc_collections_count_total{kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}[5m])
# GC 耗时
rate(jvm_gc_collections_elapsed_total{kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}[5m])
```

#### 获取 JVM 线程数

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/thread
```

**PromQL**:
```promql
jvm_threads_count{kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}
```

#### 获取 JVM 内存池明细

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/memory-pools
```

**PromQL**:
```promql
jvm_memory_pool_used_bytes{kubernetes_namespace="${ns}", kubernetes_pod_name="${name}"}
```

### 4.6 集群概览

```
GET /container/clusters/{clusterId}/monitor/overview
```

聚合多个 PromQL 查询，直接返回预计算的值（非时序）：

```json
{
  "code": 200,
  "data": {
    "cpuUsagePercent": 45.2,
    "memoryUsagePercent": 62.8,
    "nodeTotal": 3,
    "nodeReady": 3,
    "podTotal": 60,
    "podRunning": 58,
    "diskReadBytesPerSec": 1048576,
    "diskWriteBytesPerSec": 524288
  }
}
```

---

## 5. 前端页面设计

### 5.1 页面 Tab 插入位置

**PodDetailView.vue** — 在"注解"Tab 前插入"监控"Tab：

| 当前 Tab 顺序 | 变更后 |
|-------------|--------|
| POD信息 / 容器 / Conditions / YAML / 日志 / 控制台 / 标签 / 注解 | + **监控** |
| | → 如果检测到 JVM 指标，监控 Tab 内部再拆分子 Tab：「基础指标」「JVM 监控」 |

**NodeDetailView.vue** — 在"镜像"Tab 前插入"监控"Tab：

| 当前 Tab 顺序 | 变更后 |
|-------------|--------|
| 节点信息 / 地址 / Taints / 镜像 / 标签 / 注解 | + **监控** |

**ClusterDetailView.vue** — 在最后追加"监控"Tab：

| 当前 Tab 顺序 | 变更后 |
|-------------|--------|
| 概览 / 节点 / 系统组件 | + **监控** |

### 5.2 集群概览监控面板（ClusterMonitorView）

```
┌─────────────────────────────────────────────────────────┐
│  时间范围: ○ 1小时  ○ 6小时  ○ 24小时  ○ 7天            │
├─────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │  CPU     │  │  内存    │  │  Pod     │  │ 节点    │ │
│  │  使用率  │  │  使用率  │  │  使用率  │  │ 就绪数  │ │
│  │  45.2%   │  │  62.8%   │  │  96.7%   │  │  3/3    │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Node CPU 趋势图 (折线)                          │   │
│  │  Y轴: CPU使用率(%)                               │   │
│  │  颜色: #2080f0                                   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────┬───────────────────────────┐   │
│  │  Node 内存趋势图     │  网络 IO 趋势图           │   │
│  │  (折线)              │  (双线: RX蓝/TX橙)        │   │
│  └──────────────────────┴───────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

- 4 个 Stat 卡片使用 `<n-statistic>` 组件 + `<n-card>`
- **CPU 使用率** = `100 - avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100`
- **内存使用率** = `(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100`
- CPU 趋势图只显示一条聚合曲线（单节点集群）

### 5.3 Node 详情监控面板（NodeMonitorView）

```
┌─────────────────────────────────────────────────────────┐
│  时间范围: ○ 1小时  ○ 6小时  ○ 24小时  ○ 7天            │
├─────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │  CPU     │  │  内存    │  │  TCP     │  │ 负载    │ │
│  │  使用率  │  │  使用率  │  │  连接数  │  │  1m     │ │
│  │  35.2%   │  │  52.8%   │  │  128     │  │  0.85   │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  CPU + 内存混合图                                 │   │
│  │  双 Y 轴: 左 CPU%(蓝, #2080f0)                   │   │
│  │           右 内存%(绿, #18a058)                   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  网络 IO (双线)                                   │   │
│  │  接收(#f0a020) / 发送(#d03050)                   │   │
│  │  Y轴: bytes/s (自动 KB/MB/GB)                    │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────┬───────────────────────────┐   │
│  │  磁盘 IO (双线)      │  TCP 连接数趋势           │   │
│  │  读(#a060d0)/写(#18a058)│ (单线 #2080f0)         │   │
│  └──────────────────────┴───────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 5.4 Pod 详情监控面板（PodMonitorView）

```
┌─────────────────────────────────────────────────────────┐
│  时间范围: ○ 1小时  ○ 6小时  ○ 24小时  ○ 7天            │
│  容器选择: [下拉选择容器]                                   │
├─────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │  CPU     │  │  内存    │  │  网络 RX │  │ 网络 TX │ │
│  │  使用    │  │  使用    │  │  速率    │  │  速率   │ │
│  │  125m    │  │  256MB   │  │  1.2MB/s│  │  0.3MB/s│ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  CPU 使用率时序图                                 │   │
│  │  带 request/limit 参考线(虚线)                   │   │
│  │  Y轴: millicores                                 │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  内存使用时序图                                   │   │
│  │  带 request/limit 参考线(虚线)                   │   │
│  │  Y轴: bytes (自动 KB/MB/GB)                      │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  网络 IO (RX/TX 双线)                            │   │
│  │  Y轴: bytes/s                                    │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

- 容器选择下拉：仅当 Pod 有多个容器时显示
- request/limit 参考线：从 kube-state-metrics 查询 Pod 的 resource spec

### 5.5 路由和 props 设计

监控组件作为子视图嵌入到现有页面中，不增加独立路由。使用 `<component :is="...">` 动态渲染 Tab 面板。

```typescript
// containerRoutes.ts 无需新增路由，直接在现有页面 Tab 中引用组件
```

---

## 6. Java JVM 监控面板

### 6.1 检测机制

Pod 详情页在加载监控 Tab 时，先调用检测端点判断 Pod 是否暴露 JVM 指标：

```
GET /container/clusters/{clusterId}/monitor/namespaces/{ns}/pods/{name}/jvm/check
```

**响应**: `{ "hasJvmMetrics": true/false }`

实现方式：查询 `jvm_memory_used_bytes{kubernetes_pod_name="${name}"}` 是否返回非空结果。

检测为 true 时，监控 Tab 内部拆分为两个子 Tab：「基础指标」「JVM 监控」。

### 6.2 JVM 面板布局

```
┌─────────────────────────────────────────────────────────┐
│  JVM 基础统计                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │  堆内存  │  │  非堆    │  │  线程数  │  │  GC频率 │ │
│  │  使用率  │  │  使用    │  │  当前    │  │  次/min │ │
│  │  68%     │  │  128MB   │  │  24      │  │  2.3    │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  JVM 堆内存                                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │  三线: 已用(#2080f0) / 承诺(#f0a020) / 上限(虚线#aaa)│
│  │  Y轴: bytes (自动 KB/MB/GB)                       │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────┬───────────────────────────┐   │
│  │  内存池分布(堆叠面积图)│  GC 统计(柱状图)          │   │
│  │  G1 Eden  ██ 60%     │  Young ■ Full             │   │
│  │  Survivor ██ 15%     │  ┌────┬────┬────┐        │   │
│  │  Old      ██ 25%     │  │ ██ │ ██ │ █  │        │   │
│  │  Metaspace █ 5%      │  └────┴────┴────┘        │   │
│  └──────────────────────┴───────────────────────────┘   │
│                                                          │
│  ┌──────────────────────┬───────────────────────────┐   │
│  │  线程数趋势(折线)    │  类加载趋势(折线)          │   │
│  └──────────────────────┴───────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 6.3 PromQL 映射

| Stat 卡片 | PromQL |
|-----------|--------|
| 堆内存使用率 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_limit_bytes{area="heap"} * 100` |
| 非堆使用 | `jvm_memory_used_bytes{area="nonheap"}` |
| 线程数 | `jvm_threads_count` |
| GC 频率 | `rate(jvm_gc_collections_count_total[5m])` |

---

## 7. 图表组件设计

### 7.1 组件结构

```
components/
  MonitorTimeRange.vue     # 时间范围选择器（n-radio-group）
  MonitorStatCard.vue      # 统计卡片（封装 n-card + n-statistic + n-spin）
  MonitorLineChart.vue     # 折线图（ECharts 封装，含 loading/empty/error 态）
  MonitorStackChart.vue    # 堆叠面积图（ECharts 封装）
  MonitorBarChart.vue      # 柱状图（ECharts 封装）
```

### 7.2 MonitorLineChart 设计

```vue
<script setup lang="ts">
import type { MonitorSeries } from '@/modules/container/types/monitor'

const props = defineProps<{
  title: string
  series: MonitorSeries[]
  yAxisLabel?: string        // Y轴单位, 如 "millicores" "bytes" "%"
  loading?: boolean
  empty?: boolean
  error?: string | null
  lines?: { label: string; color: string; data: [number, number][] }[]
  referenceLines?: { value: number; label: string; color: string }[]  // 参考线
}>()
</script>
```

### 7.3 颜色规范

| 指标 | 颜色值 | 用途 |
|------|--------|------|
| CPU | `#2080f0` (Naive blue) | CPU 使用率折线 |
| 内存 | `#18a058` (Naive green) | 内存使用率折线 |
| 网络 RX | `#f0a020` (Naive orange) | 网络接收折线 |
| 网络 TX | `#d03050` (Naive red) | 网络发送折线 |
| 磁盘读 | `#a060d0` (purple) | 磁盘读取折线 |
| 磁盘写 | `#18a058` (green) | 磁盘写入折线 |
| TCP | `#2080f0` (blue) | 连接数折线 |
| Request 线 | `#aaa` 虚线 | Pod request 参考线 |
| Limit 线 | `#d03050` 虚线 | Pod limit 参考线 |
| JVM 堆已用 | `#2080f0` | 堆已用 |
| JVM 堆承诺 | `#f0a020` | 堆承诺 |
| JVM 堆上限 | `#aaa` 虚线 | 堆上限 |
| Young GC | `#2080f0` | Young GC 柱状图 |
| Full GC | `#d03050` | Full GC 柱状图 |

### 7.4 数值格式化工具

```typescript
// src/modules/container/utils/format.ts 新增
export function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return (bytes / 1024 ** 3).toFixed(1) + ' GB'
  if (bytes >= 1024 ** 2) return (bytes / 1024 ** 2).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

export function formatBytesPerSec(bytes: number): string {
  return formatBytes(bytes) + '/s'
}

export function formatCpu(millicores: number): string {
  if (millicores >= 1000) return (millicores / 1000).toFixed(2) + ' core'
  return millicores.toFixed(0) + ' m'
}

export function formatPercent(value: number): string {
  return value.toFixed(1) + '%'
}
```

---

## 8. 边界情况与错误处理

### 8.1 Prometheus 未配置

当集群 `labels` 中 **没有** `prometheusUrl` 时：

- 后端：返回 `BaseResult.error(400, "该集群未配置 Prometheus 地址")`
- 前端：监控 Tab 内显示 `<n-empty description="该集群未配置 Prometheus，请先部署 prometheus-deployment.md">`，并附一个"查看部署文档"链接

### 8.2 Prometheus 连接失败

后端调用 Prometheus API 超时或连接拒绝时：

- 后端：返回 `BaseResult.error(502, "Prometheus 连接失败: ${e.message}")`
- 前端：错误块内显示 `<n-alert type="error" closable>`，可点击"重试"按钮

### 8.3 数据不存在

PromQL 返回空结果集（Pod 刚创建尚无数据 / 指标名不存在）：

- 后端：返回空的 `data: []`
- 前端：图表区域显示 `<n-empty description="暂无数据">`，不阻塞其他图表

### 8.4 非 Java Pod

`/jvm/check` 端点返回 `{ hasJvmMetrics: false }` 时：

- 前端：监控 Tab 内只显示基础指标（CPU/内存/网络），不显示 JVM 子 Tab

### 8.5 多容器 Pod

- 容器选择下拉框列出 Pod 内所有容器名
- 切换容器时重新查询（CPU/内存指标按 container 过滤）
- 网络 IO 按 Pod 聚合，不按容器

### 8.6 时间范围切换

- 切换 range 时重新加载所有图表
- 每个图表组件独立 loading，切换时不阻塞整个页面
- step 自动计算规则：

| range | step | 数据点数 |
|-------|------|---------|
| 1h | 15s | ~240 |
| 6h | 1m | ~360 |
| 24h | 5m | ~288 |
| 7d | 30m | ~336 |

---

## 9. 实施计划

### Phase 1：后端基础

1. 添加 WebClient 依赖到 `container-core/pom.xml`
2. 创建 `PrometheusClient.java`（封装 query / query_range HTTP 调用）
3. 创建 `PrometheusQueryBuilder.java`（PromQL 模板方法）
4. 创建 `MonitorService.java`（组装 PromQL、解析 Prometheus 响应、格式化）
5. 创建 `MonitorSeriesVO.java` / `MonitorPointVO.java` / `MonitorOverviewVO.java`
6. 创建 `MonitorController.java`（全部 11+ 端点）

### Phase 2：前端基础组件

1. 安装 `echarts` + `vue-echarts`
2. 创建 `src/modules/container/api/monitor.ts`
3. 创建 `src/modules/container/types/monitor.ts`
4. 创建 `MonitorTimeRange.vue`
5. 创建 `MonitorStatCard.vue`
6. 创建 `MonitorLineChart.vue` / `MonitorStackChart.vue` / `MonitorBarChart.vue`

### Phase 3：页面集成

1. 创建 `ClusterMonitorView.vue` → 嵌入 ClusterDetailView
2. 创建 `NodeMonitorView.vue` → 嵌入 NodeDetailView
3. 创建 `PodMonitorView.vue` → 嵌入 PodDetailView
4. 创建 `PodJvmMonitor.vue` → 在 PodMonitorView 中条件渲染

### Phase 4：验证

1. 确认 `devops-k3s` 容器运行且 Prometheus 已部署
2. 确认 k3s 集群 labels 中 `prometheusUrl` 已配置
3. 后端 API 测试：`curl http://localhost:8089/container/clusters/1/monitor/overview`
4. 前端验证：Pod 详情页监控 Tab → 图表渲染正确
5. 前端验证：时间范围切换、容器选择切换正常
6. 前端验证：loading / empty / error 三种状态显示正确

---

## 10. 新增文件清单

### 后端文件（5 个）

| 文件路径 | 说明 |
|----------|------|
| `backend/container-core/src/main/java/com/hfwas/devops/container/dto/MonitorSeriesVO.java` | 时序数据响应 VO |
| `backend/container-core/src/main/java/com/hfwas/devops/container/dto/MonitorPointVO.java` | 单个时序点 VO |
| `backend/container-core/src/main/java/com/hfwas/devops/container/dto/MonitorOverviewVO.java` | 集群概览聚合 VO |
| `backend/container-core/src/main/java/com/hfwas/devops/container/controller/MonitorController.java` | 监控 REST 控制器 |
| `backend/container-core/src/main/java/com/hfwas/devops/container/service/prometheus/PrometheusClient.java` | Prometheus HTTP API 客户端 |
| `backend/container-core/src/main/java/com/hfwas/devops/container/service/prometheus/PrometheusQueryBuilder.java` | PromQL 构造器 |
| `backend/container-core/src/main/java/com/hfwas/devops/container/service/prometheus/MonitorService.java` | 监控业务服务 |

### 前端文件（12 个）

| 文件路径 | 说明 |
|----------|------|
| `frontend/src/modules/container/api/monitor.ts` | 监控 API |
| `frontend/src/modules/container/types/monitor.ts` | 监控类型 |
| `frontend/src/modules/container/components/MonitorTimeRange.vue` | 时间范围选择器 |
| `frontend/src/modules/container/components/MonitorStatCard.vue` | 统计卡片 |
| `frontend/src/modules/container/components/MonitorLineChart.vue` | 折线图 |
| `frontend/src/modules/container/components/MonitorStackChart.vue` | 堆叠面积图 |
| `frontend/src/modules/container/components/MonitorBarChart.vue` | 柱状图 |
| `frontend/src/modules/container/views/monitor/ClusterMonitorView.vue` | 集群概览监控面板 |
| `frontend/src/modules/container/views/monitor/NodeMonitorView.vue` | Node 详情监控面板 |
| `frontend/src/modules/container/views/monitor/PodMonitorView.vue` | Pod 详情监控面板 |
| `frontend/src/modules/container/views/monitor/PodJvmMonitor.vue` | Java Pod JVM 监控面板 |

### 需修改的文件（5 个）

| 文件路径 | 修改内容 |
|----------|----------|
| `frontend/package.json` | 加 `echarts` + `vue-echarts` 依赖 |
| `frontend/src/modules/container/views/PodDetailView.vue` | 增加"监控"Tab，引用 PodMonitorView |
| `frontend/src/modules/container/views/NodeDetailView.vue` | 增加"监控"Tab，引用 NodeMonitorView |
| `frontend/src/modules/container/views/ClusterDetailView.vue` | 增加"监控"Tab，引用 ClusterMonitorView |
| `backend/container-core/pom.xml` | 加 spring-boot-starter-webflux 依赖（如需） |