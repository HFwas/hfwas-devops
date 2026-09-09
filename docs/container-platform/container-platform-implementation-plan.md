# 容器管理平台 — 实施计划（Phase 1）

> 日期：2026-09-10  
> 状态：待实施  
> 版本：v0.2  
> 关联： [容器管理平台总体设计方案](./container-platform-design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-10 | 初版：Phase 1 实施计划，多集群管理 + K8s 资源 CRUD + Pod 详情/日志/事件 |
| v0.2 | 2026-09-10 | 按审查修订：租户过滤、namespace 路径、模块接线、Event 不落库、VO 出站、新增而非替换产品位 |

---

## Context

根据 `docs/container-platform/container-platform-design.md` v0.2 实施 **Phase 1**。

**Phase 1 验收（仅此范围）：**

- 多集群纳管：注册 / 列表 / 详情 / 更新 / 删除 / 连接测试 / 资源统计；`tenant_id` 必填，按 `X-Tenant-Id` 过滤
- 只读资源：Namespace、Pod、Deployment、Service 的列表与详情 + Pod/Deploy YAML（只读）
- 有限变更：删除 Pod；Deployment 扩缩容 / 重启
- Pod 日志 Tail（kube API）+ 按 UID 查 Event
- 模块可运行：`container-core` 被 server 依赖、`container-schema.sql` 已登记、心跳任务存在

**Phase 1 明确不做：** 完整 CRUD / 创建资源 / 终端 Tab / 拓扑 / 监控 / 镜像仓库 / 全量 Event 落库 / 项目↔Namespace / K8s RBAC 同步 / `direct` 模式 / web kubectl。

### 现有基础设施

| 领域 | 技术 |
|------|------|
| **后端** | Spring Boot 3.4 + Java 21 + fabric8 **7.8.0** + MyBatis-Plus + SQLite |
| **前端** | Vue 3 + TypeScript + **Naive UI** + Pinia |
| **现有 K8s 集成** | pipeline-core 单集群文件 kubeconfig Bean、Pod exec（server 模块裸 WebSocket） |
| **认证** | Keycloak OAuth2 JWT + `X-Tenant-Id` + `TenantContextFilter` |
| **API 前缀** | 后端 `/pipeline`、`/pm`（无 `/api`）；前端 axios `baseURL: '/api'` |
| **分页** | 后端 `IPage<T>`，前端 `PageResult<T>` |
| **模块模式** | 后端多模块 Maven，前端 `modules/`；pipeline 控制器在 core 内，靠 `DevopsApplication` 包扫描 |

### 与 pipeline-core 共存

Phase 1 **不替换** `PipelineExecutorConfiguration` 的 `KubernetesClient` Bean。`container-core` 使用独立的 `ClusterKubernetesClientFactory`。Phase 2 再把流水线执行集群改为已纳管 `clusterId`。

---

## 实施步骤

### Step 1: 后端 — 新建 `container-core` 并接入运行时

仿照 `pipeline-core`（`@Configuration` + `@ComponentScan` + `@MapperScan`，**不必**强上 AutoConfiguration SPI）。

**新增 / 修改：**

- `backend/container-core/pom.xml` — parent = backend, artifactId = container-core
- `backend/container-core/src/main/java/com/hfwas/devops/container/config/ContainerCoreAutoConfiguration.java`
- `backend/pom.xml` — `<module>container-core</module>`
- **`backend/server/pom.xml` — 增加 `container-core` 依赖（缺此项模块不会进 classpath）**
- `backend/server/src/main/resources/db/container-schema.sql`
- **`SqliteSchemaInitializer` — `populator.addScript(.../db/container-schema.sql)`**

**依赖**：spring-boot-starter-web, spring-boot-starter-websocket, user-api, mybatis-plus, lombok, fabric8 kubernetes-client（版本走 parent BOM 7.8.0）

`container-schema.sql` 至少包含 `cluster_info`（见 Step 2）。Phase 1 **不建** `container_event` 表。

---

### Step 2: 后端 — Cluster 实体、加密、Factory、心跳

**`cluster_info` 字段：**

- id, **tenant_id（必填）**, name（租户内唯一）, alias, provider, version
- kubeconfig（AES-256-GCM 密文）
- mode（Phase 1 固定 `proxy`，不实现 `direct`）
- status, labels(JSON), created_at, updated_at

**加密**：仿照 `CredentialCipher` 新增 `KubeconfigCipher`，key 使用独立配置 `container.credential-key`，不要复用 `pipeline.credential-key` 的默认值混用语义。

**关键类：**

| 类名 | 职责 |
|------|------|
| `ClusterMapper` | MyBatis-Plus BaseMapper |
| `ClusterService` | CRUD + 租户过滤 + 连接测试 |
| `ClusterKubernetesClientFactory` | 多集群 client 缓存 |
| `ClusterHeartbeatJob` | `@Scheduled` 心跳，更新 status |

**`ClusterKubernetesClientFactory`：**

```java
class ClusterKubernetesClientFactory {
  private final ConcurrentHashMap<Long, KubernetesClient> clientCache = new ConcurrentHashMap<>();

  KubernetesClient getClient(Long clusterId) {
    // computeIfAbsent，禁止并发双建
  }
  void evictClient(Long clusterId) {
    // remove 后 close()；更新 kubeconfig / 删除集群时必须调用
  }
  boolean testConnection(Long clusterId) { /* /readyz 或等价 */ }
  @PreDestroy
  void shutdown() { /* close 全部 */ }
}
```

心跳：已注册且本进程能解密的集群，周期探测 `/readyz` + Node Ready 数，写入 `Connected` / `Degraded` / `Disconnected`。

---

### Step 3: 后端 — Cluster Controller

路径与 `/pipeline` 一致，**无** `/api` 前缀。

- `POST /container/clusters` — 注册；写入 `currentTenantId()`
- `POST /container/clusters/page` — 分页，**强制按当前租户过滤**（与 pipeline page 风格一致）
- `GET /container/clusters/{id}` — 详情；跨租户 404
- `PUT /container/clusters/{id}` — 更新；改 kubeconfig 后 `evictClient`
- `DELETE /container/clusters/{id}` — 删除；先 evict
- `POST /container/clusters/{id}/test` — 连接测试
- `GET /container/clusters/{id}/stats` — Node/Pod/CPU/Mem 统计

**DTOs**：`ClusterSaveDTO`, `ClusterVO`, `ClusterStatsVO`，`BaseResult<T>` / `IPage<T>`。

**`ClusterVO` 禁止包含 kubeconfig**（及任何 token / certificate-authority-data）。

---

### Step 4: 后端 — ResourceService（出站 VO）

内部用 fabric8，对外只返回 VO。namespaced 方法必须带 `namespace`。调用前 `ClusterService.requireVisible(clusterId)`（当前租户）。

```java
class ResourceService {
  List<NamespaceVO> listNamespaces(Long clusterId);
  NamespaceVO getNamespace(Long clusterId, String name);

  IPage<PodSummaryVO> listPods(Long clusterId, String namespace, String keyword, int page, int size);
  PodDetailVO getPod(Long clusterId, String namespace, String name);
  String getPodYaml(Long clusterId, String namespace, String name);
  void deletePod(Long clusterId, String namespace, String name);

  IPage<DeploymentSummaryVO> listDeployments(Long clusterId, String namespace, String keyword, int page, int size);
  DeploymentDetailVO getDeployment(Long clusterId, String namespace, String name);
  void scaleDeployment(Long clusterId, String namespace, String name, int replicas);
  void restartDeployment(Long clusterId, String namespace, String name);

  IPage<ServiceSummaryVO> listServices(Long clusterId, String namespace, String keyword, int page, int size);
  ServiceDetailVO getService(Long clusterId, String namespace, String name);
}
```

Phase 1 **不做** `rollbackDeployment`（需 revision 历史，放到 YAML 应用阶段）。YAML 只读。

---

### Step 5: 后端 — 资源 Controllers

列表用 query；**详情 / YAML / 日志快照 / 删除必须带 namespace**。

| Controller | Endpoints |
|------------|-----------|
| **NamespaceController** | `GET /container/clusters/{clusterId}/namespaces` |
| **PodController** | `GET /container/clusters/{clusterId}/pods?namespace=&keyword=&pageNo=&pageSize=` |
| | `GET /container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}` |
| | `GET .../pods/{name}/yaml` |
| | `GET .../pods/{name}/logs`（当前快照，非 WS） |
| | `DELETE .../pods/{name}` |
| **DeploymentController** | `GET /container/clusters/{clusterId}/deployments?namespace=` |
| | `GET /container/clusters/{clusterId}/namespaces/{namespace}/deployments/{name}` |
| | `GET .../deployments/{name}/yaml` |
| | `PUT .../deployments/{name}/scale` |
| | `PUT .../deployments/{name}/restart` |
| | `DELETE .../deployments/{name}` |
| **ServiceController** | `GET /container/clusters/{clusterId}/services?namespace=` |
| | `GET /container/clusters/{clusterId}/namespaces/{namespace}/services/{name}` |
| **EventController** | `GET /container/clusters/{clusterId}/namespaces/{namespace}/events?uid=` |

缺 namespace 的详情请求直接 400，不用默认 `default`。

---

### Step 6: 后端 — WebSocket Watch 与日志 Tail

**裸 WebSocket**，与 `PodExecWebSocketConfig` 相同模式（不要 STOMP）。

| Handler | 路径 |
|---------|------|
| `ResourceWatchHandler` | `WS /ws/container/watch/{clusterId}?resources=Pod,Deployment,Service&namespace=` |
| `LogTailHandler` | `WS /ws/container/logs/{clusterId}?namespace=&pod=&container=` |

消息：`{"type":"ADDED|MODIFIED|DELETED","resource":"Pod","object":{/* ResourceWatchVO 摘要 */}}`。**不推送 fabric8 全量对象。**

**`ContainerWsAuthInterceptor`**：

1. 从 `Sec-WebSocket-Protocol` 取 JWT（同 `PodExecAuthHandshakeInterceptor`）
2. 解析租户（header 或 JWT 后的 membership，与 `TenantContextService` 对齐）
3. 校验 `clusterId` 对当前租户可见
4. namespaced 查询参数缺失则拒绝握手
5. 失败关闭连接，不建立 Watch / Tail

**`ContainerWsConfig`**：注册上述 handler + interceptor。

Watch 仅当前 Namespace；集群删除或 evict 时关掉该集群上的 Watch。按租户限制连接数。

前端开发代理：沿用现有 `/api` → 后端且 `ws: true`，浏览器连 `ws://host/api/ws/container/...`（与 pipeline 终端一致）。

---

### Step 7: 后端 — Event 查询（不落库）

**`EventQueryService`**：`client.v1().events().inNamespace(ns)` + `involvedObject.uid` 过滤，映射为 `EventVO`。

- **不做** `SharedInformer` 全集群常驻
- **不做** `container_event` 表 / `ContainerEventEntity`
- 可选：当前 Namespace 的短生命周期 Watch + 内存 ring buffer（≤500），断连即丢；不进本步验收也可

`EventController` 见 Step 5。

---

### Step 8: 后端 — SecurityConfig

**不是匿名白名单。** REST 显式要求登录，与 `anyRequest().authenticated()` 一致；WS 因浏览器无法带 `Authorization` 头而 `permitAll`，鉴权只在握手拦截器。

```java
.requestMatchers("/container/**").authenticated()
.requestMatchers("/ws/container/**").permitAll()  // 鉴权在 ContainerWsAuthInterceptor
```

---

### Step 9: 后端 — 单元测试

- `KubeconfigCipherTest` — 加解密
- `ClusterServiceTest` — CRUD、**跨租户 404**、kubeconfig 不出现在 VO
- `ResourceServiceTest` — list/get/delete；缺 namespace 拒绝（可用 mock client）
- `ClusterKubernetesClientFactoryTest` — evict 后 close（mock）

---

### Step 10: 前端 — 新建 container 模块

```
frontend/src/modules/container/
  api/          cluster.ts, pod.ts, deployment.ts, service.ts, event.ts
  router/       containerRoutes.ts
  types/        cluster.ts, resource.ts, event.ts
  views/
    ContainerShell.vue
    ClusterListView.vue
    ClusterDetailView.vue
    PodListView.vue
    PodDetailView.vue          — 概览 / YAML / 日志 / 事件（无终端）
    DeploymentListView.vue
    DeploymentDetailView.vue
    ServiceListView.vue
    ServiceDetailView.vue
  components/
    PodLogViewer.vue
    ResourceYamlViewer.vue     — 只读
    ResourceStatusBadge.vue
  stores/       cluster.ts
```

---

### Step 11: 前端 — 类型定义

```typescript
export interface ClusterVO {
  id: number
  name: string
  alias: string
  provider: string
  version: string
  status: 'Connected' | 'Degraded' | 'Disconnected' | 'Unknown'
  nodeCount?: number
  podCount?: number
  labels: Record<string, string>
  createdAt: string
  // 无 kubeconfig
}

export interface PodSummary {
  name: string
  namespace: string
  status: string
  nodeName: string
  podIP: string
  containerCount: number
  restarts: number
  age: string
}
```

---

### Step 12: 前端 — API 层

axios 已加 `/api`，此处路径与后端一致：

```typescript
export const clusterApi = {
  page: (data) => post<PageResult<ClusterVO>>('/container/clusters/page', data),
  get: (id) => get<ClusterVO>(`/container/clusters/${id}`),
  create: (data) => post<number>('/container/clusters', data),
  update: (id, data) => put(`/container/clusters/${id}`, data),
  delete: (id) => del(`/container/clusters/${id}`),
  test: (id) => post<boolean>(`/container/clusters/${id}/test`),
  stats: (id) => get<ClusterStatsVO>(`/container/clusters/${id}/stats`),
}

export const podApi = {
  page: (clusterId, query) =>
    get<PageResult<PodSummary>>(`/container/clusters/${clusterId}/pods`, query),
  get: (clusterId, namespace, name) =>
    get<PodDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),
  yaml: (clusterId, namespace, name) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/yaml`),
  remove: (clusterId, namespace, name) =>
    del(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),
}
```

---

### Step 13: 前端 — Pinia Store

```typescript
export const useClusterStore = defineStore('container-cluster', () => {
  const currentCluster = ref<ClusterVO | null>(null)
  const clusterList = ref<ClusterVO[]>([])
  async function fetchClusters() { /* page 当前租户 */ }
  function setCurrent(cluster: ClusterVO) { currentCluster.value = cluster }
  return { currentCluster, clusterList, fetchClusters, setCurrent }
})
```

租户切换时应清空 `currentCluster` / `clusterList`（监听 auth store 的 tenant）。

---

### Step 14: 前端 — 路由

```typescript
export const containerRoutes: RouteRecordRaw[] = [
  {
    path: '/container',
    component: () => import('@/modules/container/views/ContainerShell.vue'),
    children: [
      { path: '', redirect: '/container/clusters' },
      { path: 'clusters', name: 'container-clusters', component: () => import('@/modules/container/views/ClusterListView.vue') },
      { path: 'clusters/:id', name: 'container-cluster-detail', component: () => import('@/modules/container/views/ClusterDetailView.vue') },
      { path: 'clusters/:clusterId/pods', name: 'container-pods', component: () => import('@/modules/container/views/PodListView.vue') },
      { path: 'clusters/:clusterId/namespaces/:namespace/pods/:name', name: 'container-pod-detail', component: () => import('@/modules/container/views/PodDetailView.vue') },
      { path: 'clusters/:clusterId/deployments', name: 'container-deployments', component: () => import('@/modules/container/views/DeploymentListView.vue') },
      { path: 'clusters/:clusterId/namespaces/:namespace/deployments/:name', name: 'container-deployment-detail', component: () => import('@/modules/container/views/DeploymentDetailView.vue') },
      { path: 'clusters/:clusterId/services', name: 'container-services', component: () => import('@/modules/container/views/ServiceListView.vue') },
      { path: 'clusters/:clusterId/namespaces/:namespace/services/:name', name: 'container-service-detail', component: () => import('@/modules/container/views/ServiceDetailView.vue') },
    ],
  },
]
```

列表页用 query 或 store 记住当前 namespace；进详情必须带 `:namespace`。

在 `frontend/src/router/index.ts` 展开 `containerRoutes`。

---

### Step 15: 前端 — 控制台产品注册

**新增** `container` 产品，**保留** `resource`（资源编排）comingSoon，不要替换。

```typescript
{
  key: 'container',
  name: '容器管理',
  description: '多集群 Kubernetes 统一管理',
  icon: ServerCog,
  path: '/container/clusters',
  group: '基础设施',
}
```

`resolveActiveProductKey` 已按 `/${product.key}` 匹配，`/container/...` 会自动命中。

---

### Step 16~19: 前端页面

| 页面 | 关键功能 |
|------|----------|
| **ClusterListView** | 当前租户集群、注册、状态过滤、连接测试 |
| **ClusterDetailView** | 统计、节点列表、进入各资源列表 |
| **PodListView** | Namespace / 状态筛选、搜索；行点击带 namespace |
| **PodDetailView** | 概览 + YAML（只读）+ 日志 + 事件。**无终端 Tab** |
| **DeploymentListView / Detail** | 列表、扩缩容、重启 |
| **ServiceListView / Detail** | 类型 / 端口 / 选择器 |
| **PodLogViewer** | 多容器、WS Tail、暂停、下载当前缓冲 |

---

### Step 20: 集成测试

1. `cd backend && mvn compile -pl container-core,server -am`（确认 server 已依赖模块）
2. 启动后确认 `cluster_info` 已建（schema initializer）
3. 注册集群 → 租户 A 可见、租户 B 404
4. `GET` Pod 详情必须带 namespace；缺则 400
5. `ClusterVO` 响应无 kubeconfig
6. 前端 `npm run build`
7. 全流程：注册 → 列表 Pod → 详情 → 日志 Tail

---

## Phase 1 验收清单

- [ ] `server` 依赖 `container-core`，进程内有 Cluster Bean
- [ ] `container-schema.sql` 已加入 `SqliteSchemaInitializer`
- [ ] 集群带 `tenant_id`，跨租户不可见
- [ ] `ClusterVO` 无 kubeconfig
- [ ] 资源详情路径含 namespace
- [ ] 出站为 VO，非 fabric8 模型
- [ ] Watch / Tail 握手校验 JWT + 集群可见
- [ ] Event 按 UID 查询，无全量落库
- [ ] 心跳任务更新集群状态
- [ ] 产品目录新增 `container`，`resource` 仍在
- [ ] Pod 详情无终端入口
- [ ] pipeline 单集群 client 未被删除或替换

---

## 文件变更清单

### 新增文件（后端）

```
backend/container-core/pom.xml
backend/container-core/src/main/java/com/hfwas/devops/container/config/ContainerCoreAutoConfiguration.java
backend/container-core/src/main/java/com/hfwas/devops/container/entity/ClusterEntity.java
backend/container-core/src/main/java/com/hfwas/devops/container/mapper/ClusterMapper.java
backend/container-core/src/main/java/com/hfwas/devops/container/service/ClusterService.java
backend/container-core/src/main/java/com/hfwas/devops/container/service/ResourceService.java
backend/container-core/src/main/java/com/hfwas/devops/container/service/EventQueryService.java
backend/container-core/src/main/java/com/hfwas/devops/container/service/cluster/ClusterKubernetesClientFactory.java
backend/container-core/src/main/java/com/hfwas/devops/container/service/cluster/KubeconfigCipher.java
backend/container-core/src/main/java/com/hfwas/devops/container/job/ClusterHeartbeatJob.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/ClusterController.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/NamespaceController.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/PodController.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/DeploymentController.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/ServiceController.java
backend/container-core/src/main/java/com/hfwas/devops/container/controller/EventController.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ClusterSaveDTO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ClusterVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ClusterStatsVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/PodSummaryVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/PodDetailVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/DeploymentSummaryVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/DeploymentDetailVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ServiceSummaryVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ServiceDetailVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/NamespaceVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/EventVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/dto/ResourceWatchVO.java
backend/container-core/src/main/java/com/hfwas/devops/container/ws/ResourceWatchHandler.java
backend/container-core/src/main/java/com/hfwas/devops/container/ws/LogTailHandler.java
backend/container-core/src/main/java/com/hfwas/devops/container/ws/ContainerWsConfig.java
backend/container-core/src/main/java/com/hfwas/devops/container/ws/ContainerWsAuthInterceptor.java
backend/container-core/src/main/java/com/hfwas/devops/container/error/ContainerErrorCode.java
backend/server/src/main/resources/db/container-schema.sql
```

WebSocket 若与 pipeline 一样必须放在 `server` 模块才能复用现有 `JwtDecoder` 装配，可将 `container/ws/*` 改放到 `backend/server/src/main/java/com/hfwas/devops/ws/container/`，计划实施时二选一，不要两处各写一份。

### 修改文件（后端）

```
backend/pom.xml
backend/server/pom.xml                                         — 依赖 container-core
backend/server/src/main/java/com/hfwas/devops/config/SecurityConfig.java
backend/server/src/main/java/com/hfwas/devops/config/SqliteSchemaInitializer.java
```

### 新增文件（前端）

```
frontend/src/modules/container/api/cluster.ts
frontend/src/modules/container/api/pod.ts
frontend/src/modules/container/api/deployment.ts
frontend/src/modules/container/api/service.ts
frontend/src/modules/container/api/event.ts
frontend/src/modules/container/router/containerRoutes.ts
frontend/src/modules/container/types/cluster.ts
frontend/src/modules/container/types/resource.ts
frontend/src/modules/container/types/event.ts
frontend/src/modules/container/views/ContainerShell.vue
frontend/src/modules/container/views/ClusterListView.vue
frontend/src/modules/container/views/ClusterDetailView.vue
frontend/src/modules/container/views/PodListView.vue
frontend/src/modules/container/views/PodDetailView.vue
frontend/src/modules/container/views/DeploymentListView.vue
frontend/src/modules/container/views/DeploymentDetailView.vue
frontend/src/modules/container/views/ServiceListView.vue
frontend/src/modules/container/views/ServiceDetailView.vue
frontend/src/modules/container/components/PodLogViewer.vue
frontend/src/modules/container/components/ResourceYamlViewer.vue
frontend/src/modules/container/components/ResourceStatusBadge.vue
frontend/src/modules/container/stores/cluster.ts
```

### 修改文件（前端）

```
frontend/src/router/index.ts                    — 注册 containerRoutes
frontend/src/shared/console/products.ts         — 新增 container，保留 resource
```

---

## 实施顺序 & 依赖关系

```
Step 1  (模块 + server 依赖 + schema) ─────────┐
                                               ├──→ Step 2 (Entity/Factory/心跳) ──→ Step 3 (Cluster Ctrl)
                                               │         │
                                               │         └──→ Step 4 (ResourceService VO) ──→ Step 5 (Resource + Event Ctls)
                                               │                                      │
                                               │                                      ├──→ Step 6 (WS + 握手鉴权)
                                               │                                      └──→ Step 7 (Event 查询)
                                               │
                                               └──→ Step 8 (SecurityConfig) ──→ Step 9 (单元测试)

Step 10-19 (前端) 可与后端并行，路径与 VO 以本文为准
Step 20 全部完成后做
```
