# 交付运维平台 — 后端与前端架构设计

> 日期：2026-09-22
> 版本：v0.3
> 状态：部分实施（后端与前端骨架已落地，实现状态见 §9.7）
> 基础：cn-app-operator CRD（App / CloudService / CloudComponent）
> 参考：yunyou / ADP 能力中心架构模式

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-22 | 初版：完整后端数据模型、API 端点、包导入、参数表单、前端组件树、部署编排 |
| v0.2 | 2026-09-22 | 移除 tenant_id、移除工堪、前端技术栈改为 shadcn/vue + Tailwind CSS |
| v0.3 | 2026-09-22 | 按实际落地情况修正：后端为 Go（chi + client-go + SQLite）而非 fabric8/MyBatis、SQL 表 3 张而非 4 张、Tailwind v3.4 而非 v4；§9 前端设计改为独立应用并补实现状态与外壳/样式约定；部署向导按实际枚举改为 3 步；修正 §1、§8、§9、§10 子节编号错位 |

---

## 目录

1. [架构概览](#1-架构概览)
2. [数据模型](#2-数据模型)
3. [API 端点](#3-api-端点)
4. [包导入逻辑](#4-包导入逻辑)
5. [参数管理](#5-参数管理)
6. [集群管理](#6-集群管理)
7. [部署编排](#7-部署编排)
8. [审计追踪](#8-审计追踪)
9. [前端设计](#9-前端设计)
10. [与 cn-app-operator 集成](#10-与-cn-app-operator-集成)
11. [实现顺序](#11-实现顺序)

---

## 1. 架构概览

### 1.1 分层架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│            独立前端应用 (Vue 3 + shadcn/vue + Tailwind CSS)             │
│  ┌──────────┐  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ 集群管理  │  │ 产品目录    │  │ 部署向导     │  │ 产品卡片仪表盘   │  │
│  │ (库存页)  │  │ (导入/列表) │  │ (参数表单)   │  │ (状态聚合视图)   │  │
│  └──────────┘  └────────────┘  └──────────────┘  └──────────────────┘  │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ HTTP REST (/api/delivery/**)
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    后端 (Go 服务)                                       │
│                                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │ 包导入引擎   │  │ 参数表单渲染  │  │ 审计服务                     │  │
│  │ (zip/tar.gz) │  │ (schema→JSON)│  │ (操作记录)                   │  │
│  └─────────────┘  └──────────────┘  └──────────────────────────────┘  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │ 集群管理     │  │ CRD 客户端    │  │ SQLite 持久层                 │  │
│  │ (kubeconfig) │  │ (client-go)  │  │ (delivery_cluster/product/   │  │
│  │              │  │              │  │  deployment)                 │  │
│  └─────────────┘  └──────────────┘  └──────────────────────────────┘  │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ 创建/读取 CR (client-go K8s API)
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Kubernetes (cn-app-operator 已部署)                 │
│                                                                         │
│  ┌──────┐   ┌──────────────────────────────┐  ┌─────────────────┐     │
│  │ App  │──▶│ CloudService (产品蓝图)       │  │ CloudComponent  │     │
│  │ CR   │   │  - components[]               │──│ (组件实例)       │     │
│  │      │   │  - parameters[]               │  │  - Helm action  │     │
│  │      │   │  - imageList                  │  │  - status.phase │     │
│  └──────┘   └──────────────────────────────┘  └────────┬────────┘      │
│                                                         │               │
│                                                         ▼               │
│                                                  ┌─────────────────┐    │
│                                                  │ Helm install/   │    │
│                                                  │ upgrade/rollback│    │
│                                                  └─────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心原则

1. **UI 不执行 Helm**。UI Backend 只做两件事：(a) 把用户操作翻译为 CRD 操作，(b) 从 CRD status 读取状态返回给前端。
2. **声明式驱动**。写入 App CR → cn-app-operator 自动调和（安装/升级/回滚）。
3. **CRD Status 是权威**。本地 SQLite 只存安全敏感数据（kubeconfig）、包文件、参数草稿。部署状态始终从 CRD 读取。
4. **一个产品 = 一个 App CR**。App CR 引用 CloudService（产品蓝图），CloudService 定义多个 CloudComponent（组件实例）。
5. **不重新发明 Operator**。平台只做 UI 编排和参数管理，不实现 Helm 操作。


### 1.3 前端技术选型

#### 推荐栈

```
UI 框架层    Vue 3 + TypeScript         ← 组合式 API，类型安全
样式层       Tailwind CSS v3.4          ← class 名即语义，AI 最擅长
组件层       shadcn/vue                 ← 源码拷贝到项目里，AI 可读可改
无障碍原语   Radix Vue                  ← 弹窗/下拉/对话框/标签页等
路由/状态    Vue Router + Pinia         ← 标准选择
```

#### 为什么选 shadcn/vue + Tailwind CSS

| 对比维度 | Naive UI（原方案） | shadcn/vue（推荐） |
|---------|-------------------|--------------------|
| **组件位置** | `node_modules/`，黑盒不可见 | 拷贝到 `components/ui/`，源码就在项目里 |
| **AI 改样式** | 需要知道几十个 props API | 改 Tailwind class 即可（`text-blue-500` → `text-red-500`） |
| **AI 改结构** | 不熟 API 就编错 | 源码在上下文中，直接读直接改 |
| **定制能力** | 主题变量覆盖，受限 | 任意 HTML + Tailwind，不受限 |
| **设计质量** | Material-ish，偏旧 | 现代设计 + 完整暗色模式 |
| **组件获取** | `npm install naive-ui` | `npx shadcn-vue add button` → 源码进入项目 |
| **AI 训练覆盖** | 国内为主，AI 训练数据少 | 全球主流，AI 最熟悉的 UI 方案 |

#### 核心工作流

```bash
# 添加一个按钮组件（源码进入 components/ui/）
npx shadcn-vue add button

# 添加对话框
npx shadcn-vue add dialog

# 添加表格
npx shadcn-vue add table

# 组件文件就在项目里，AI 可以直接读和改
components/ui/
  button/
    Button.vue
    index.ts
  dialog/
    Dialog.vue
    DialogClose.vue
    DialogContent.vue
    index.ts
  table/
    Table.vue
    index.ts
```

### 1.4 与旧 Tekton 方案的关键区别

| 对比项 | 旧方案（2026-09-08） | 本方案 |
|--------|-------------------|--------|
| 执行引擎 | Tekton TaskRun | cn-app-operator (CRD 调和) |
| 参数传递 | values Secret → Job 环境变量 | App CR spec → Operator merge → Helm values |
| 组件依赖 | TaskRun 顺序编排 | CloudService topology 排序 |
| 状态跟踪 | TaskRun 轮询 → delivery_plan 状态 | App.status 聚合 → operator 回写 |
| 回滚 | 无内置支持 | ControllerRevision + Helm rollback |
| 升级 | 再导入 + 点部署 | 更新 App CR → operator diff → upgrade |

---

## 2. 数据模型

### 2.1 `delivery_cluster` — 集群注册

```sql
CREATE TABLE IF NOT EXISTS delivery_cluster (
    id                INTEGER      NOT NULL PRIMARY KEY,
    name              TEXT         NOT NULL,
    server_host       TEXT,
    kubeconfig_enc    TEXT         NOT NULL,       -- AES-256-GCM 加密
    is_current        INTEGER      NOT NULL DEFAULT 0,
    status            TEXT         NOT NULL DEFAULT 'UNKNOWN',  -- UNKNOWN / UP / DOWN
    version           TEXT,                                      -- Kubernetes 版本
    deleted           INTEGER      NOT NULL DEFAULT 0,
    create_by         INTEGER,
    update_by         INTEGER,
    create_time       TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time       TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_name
    ON delivery_cluster (name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_current
    ON delivery_cluster (is_current) WHERE deleted = 0 AND is_current = 1;
```

**说明：**

- `kubeconfig_enc` — AES-256-GCM 加密存储。密钥在配置中 `delivery.credential-key`（回退 `pipeline.credential-key`）。API 永不回传。
- `is_current` — 租户内至多一行 = 1。第一份导入自动设为当前。
- `server_host` — 导入时从 kubeconfig 解析 `clusters[].cluster.server`，只读展示。

### 2.2 `delivery_product` — 导入的包/产品

```sql
CREATE TABLE IF NOT EXISTS delivery_product (
    id                   INTEGER      NOT NULL PRIMARY KEY,
    product_key          TEXT         NOT NULL,       -- 包唯一键（manifest.json > key）
    display_name         TEXT         NOT NULL,
    package_version      TEXT         NOT NULL,
    package_dir          TEXT         NOT NULL,       -- 解压目录路径（持久化）
    manifest_json        TEXT         NOT NULL,       -- 原始 manifest.json 内容
    -- CloudService 关联
    cloud_service_name   TEXT,                        -- 对应 CloudService CR name
    cloud_service_ns     TEXT,                        -- CloudService CR namespace
    -- 参数快照（最新保存的值）
    params_json          TEXT         NOT NULL DEFAULT '{}',   -- 填写的参数 { path: value }
    global_params_json   TEXT         NOT NULL DEFAULT '{}',   -- 全局参数
    -- 部署状态
    status               TEXT         NOT NULL DEFAULT 'NOT_DEPLOYED',
    -- NOT_DEPLOYED / DEPLOYING / READY / DEGRADED / FAILED / UNINSTALLING
    -- 部署时冻结
    cluster_id           INTEGER,                     -- 首次部署时写入
    target_ns            TEXT,                        -- 目标 namespace
    deleted              INTEGER      NOT NULL DEFAULT 0,
    create_by            INTEGER,
    update_by            INTEGER,
    create_time          TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time          TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_product_key
    ON delivery_product (product_key) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_delivery_product_tenant
    ON delivery_product (deleted);
```

**导入流程：** 包 zip 只落盘 `data/delivery/{productId}/`（一份 `package.zip` + 解压的 `extracted/` 目录）。不把 blob 进 SQLite。

**再导入：** 同一 `product_key` → 覆盖 `package_dir`、`manifest_json`、`package_version`。保留 `params_json`（新参数补默认值，消失的参数删除）。**不**自动升级（要点"部署"）。

### 2.3 `delivery_deployment` — 部署记录

```sql
CREATE TABLE IF NOT EXISTS delivery_deployment (
    id                       INTEGER      NOT NULL PRIMARY KEY,
    product_id               INTEGER      NOT NULL,
    cluster_id               INTEGER      NOT NULL,
    action                   TEXT         NOT NULL,        -- DEPLOY / UPGRADE / ROLLBACK / UNINSTALL
    package_version          TEXT         NOT NULL,
    -- 参数快照（部署时冻结）
    params_snapshot_json     TEXT         NOT NULL,
    global_params_snapshot   TEXT         NOT NULL,
    -- App CR 引用（部署后回填）
    app_name                 TEXT,
    app_ns                   TEXT,
    -- 状态
    status                   TEXT         NOT NULL DEFAULT 'PENDING',
    -- PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED
    error_message            TEXT,
    started_at               TEXT,
    finished_at              TEXT,
    create_by                INTEGER,
    create_time              TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_deployment_product
    ON delivery_deployment (product_id, id DESC);
```

**说明：** 每次部署、升级、回滚、卸载操作都生成一条 deployment 记录。

---

## 3. API 端点

所有端点前缀 `/delivery`。使用统一响应格式 `BaseResult<T>`。

### 3.1 系统概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/status` | 平台概览：当前集群、已注册集群数、产品统计 |

```json
{
  "currentCluster": { "id": 1, "name": "k3d-dev", "status": "UP", "version": "v1.30.0" },
  "clusterCount": 3,
  "productCounts": { "total": 10, "deployed": 6, "notDeployed": 4, "failed": 1 }
}
```

### 3.2 集群管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/clusters` | 集群列表（无 kubeconfig 字段） |
| POST | `/delivery/clusters` | 导入集群（multipart name+file 或 JSON name+kubeconfigText） |
| PUT | `/delivery/clusters/{id}` | 更新名称或替换 kubeconfig |
| POST | `/delivery/clusters/{id}/current` | 设为当前集群 |
| GET | `/delivery/clusters/{id}/status` | 集群探活（k8s version / 节点 / 组件） |
| DELETE | `/delivery/clusters/{id}` | 删除（不可为当前集群，不可有已部署产品） |

**POST /delivery/clusters 请求：**
```json
{ "name": "production-cluster", "kubeconfigText": "apiVersion: v1\nclusters:\n- ..." }
```

**GET /delivery/clusters 响应：**
```json
{
  "code": 0,
  "data": [
    {
      "id": 1, "name": "k3d-dev", "serverHost": "https://127.0.0.1:6443",
      "isCurrent": true, "status": "UP", "version": "v1.30.0"
    }
  ]
}
```

### 3.3 集群库存查询（只读，打指定集群）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/clusters/{id}/nodes` | 节点列表 |
| GET | `/delivery/clusters/{id}/components` | 系统组件（kube-system） |
| GET | `/delivery/clusters/{id}/storage-classes` | StorageClass 列表 |
| GET | `/delivery/clusters/{id}/pvcs?namespace=` | PVC 列表 |
| GET | `/delivery/clusters/{id}/namespaces` | Namespace 列表 |

### 3.4 产品管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/delivery/products/page` | 分页查询 `{ pageNo, pageSize, keyword? }` |
| POST | `/delivery/products/import` | 导入包（`multipart file`，zip/tar.gz） |
| GET | `/delivery/products/{id}` | 产品详情（含 form schema、参数值、当前状态） |
| PUT | `/delivery/products/{id}` | 保存参数、displayName |
| POST | `/delivery/products/{id}/deploy` | 部署/升级（创建/更新 App CR） |
| POST | `/delivery/products/{id}/rollback` | 回滚到上一版本 |
| POST | `/delivery/products/{id}/uninstall` | 卸载（删除 App CR） |
| DELETE | `/delivery/products/{id}` | 删除产品记录（需先卸载） |

**GET /delivery/products/{id} 响应结构：**
```json
{
  "id": 1,
  "productKey": "order-platform",
  "displayName": "订单服务平台",
  "packageVersion": "1.2.0",
  "status": "READY",
  "clusterName": "k3d-dev",
  "targetNs": "dlv-order-platform",
  "cloudServiceName": "csvc-order-platform",
  "phase": "Ready",
  "aggregatedSummary": {
    "totalComponents": 4, "readyComponents": 4,
    "totalServices": 1, "readyServices": 1
  },
  "form": {
    "groups": [
      {
        "id": "global", "title": "全局参数",
        "fields": [
          { "path": "globalParams.imageRegistry", "label": "镜像仓库", "type": "string", "default": "registry.example.com" },
          { "path": "globalParams.defaultStorageClass", "label": "默认存储类", "type": "string", "default": "local-path" },
          { "path": "globalParams.domainSuffix", "label": "域名后缀", "type": "string", "default": "example.com" }
        ]
      },
      {
        "id": "component.order-platform.redis", "title": "组件 / Redis",
        "fields": [
          { "path": "overrides.order-platform.redis.replicaCount", "label": "Redis 副本数", "type": "integer", "default": 1 },
          { "path": "overrides.order-platform.redis.persistence.size", "label": "Redis 存储", "type": "string", "default": "10Gi" }
        ]
      }
    ]
  },
  "globalParams": { "imageRegistry": "registry.example.com", "defaultStorageClass": "local-path" },
  "params": { "replicaCount": 3 },
  "overrides": { "order-platform": { "redis": { "persistence": { "size": "20Gi" } } } },
  "serviceStatuses": [{ "name": "csvc-order-platform", "phase": "Ready" }]
}
```

### 3.5 部署进度

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/products/{id}/deployments` | 部署历史列表 |
| GET | `/delivery/deployments/{deployId}` | 单次部署详情（当前状态 + App CR 状态聚合） |
| POST | `/delivery/deployments/{deployId}/cancel` | 取消部署 |

### 3.6 产品资源查看

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/products/{id}/resources` | 产品部署后的 K8s 资源（Pods / SVC / Ingress / PVC） |
| GET | `/delivery/products/{id}/components` | CloudComponent 状态列表 |
| GET | `/delivery/resources/{apiVersion}/{kind}/{name}/logs` | Pod 日志 |

---

## 4. 包导入逻辑

### 4.1 包格式规范

zip 或 tar.gz，最大 100MiB。

```
order-platform-1.2.0/
  manifest.json
  charts/
    redis/
    order-backend/
    order-frontend/
  hooks/
    init-db.sql
```

### 4.2 manifest.json 规范

```json
{
  "apiVersion": "hfwas.delivery/v2",
  "key": "order-platform",
  "version": "1.2.0",
  "displayName": "订单服务平台",
  "description": "包含用户管理、订单、支付等核心业务模块",
  "icon": "https://example.com/icon.png",
  "components": [
    {
      "name": "redis",
      "displayName": "Redis 缓存",
      "componentType": "Service",
      "chart": { "repository": "oci://registry.example.com/charts", "name": "redis", "version": "17.3.0" },
      "defaultValues": { "replicaCount": 1, "persistence": { "size": "10Gi" } },
      "parameters": [
        { "name": "replicaCount", "type": "integer", "defaultValue": 1, "required": true, "path": "replicaCount", "validation": { "minimum": 1, "maximum": 10 } },
        { "name": "storageSize", "displayName": "存储大小", "type": "string", "defaultValue": "10Gi", "path": "persistence.size", "validation": { "pattern": "^[0-9]+(Gi|Ti)$" } }
      ],
      "dependencies": [],
      "resourceRequirements": { "cpu": "500m", "memory": "1Gi", "storage": "10Gi" }
    },
    {
      "name": "backend",
      "displayName": "后端服务",
      "componentType": "Service",
      "chart": { "repository": "oci://registry.example.com/charts", "name": "order-backend", "version": "2.1.0" },
      "defaultValues": { "replicaCount": 2, "image": { "tag": "2.1.0" } },
      "parameters": [
        { "name": "replicaCount", "type": "integer", "defaultValue": 2, "path": "replicaCount" },
        { "name": "memoryLimit", "displayName": "内存限制", "type": "string", "defaultValue": "4Gi", "path": "resources.limits.memory" }
      ],
      "dependencies": [
        { "component": "redis", "type": "Hard", "condition": "Ready" }
      ],
      "resourceRequirements": { "cpu": "2000m", "memory": "4Gi", "minNodeCount": 2 }
    }
  ],
  "parameters": [
    { "name": "globalReplicas", "displayName": "全局副本数", "type": "integer", "defaultValue": 3, "path": "replicaCount", "scope": "service", "targetComponents": ["backend", "frontend"] }
  ],
  "imageList": [
    { "name": "backend-image", "image": "registry.example.com/order/backend", "tag": "2.1.0", "component": "backend" },
    { "name": "redis-image", "image": "docker.io/redis", "tag": "7.2.0", "component": "redis" }
  ]
}
```

### 4.3 导入流程

```
用户上传 zip/tar.gz
    │
    ▼
1. 校验格式（扩展名、大小 ≤ 100MiB）
2. 解压到临时目录
    │
    ▼
3. 定位 manifest.json（根目录或根下唯一子目录）
    │  - apiVersion 须为 hfwas.delivery/v2
    │  - 必填字段：key / version / displayName / components
    │  - 每个 component 须有 name / chart / componentType
    │  - chart 目录须存在（charts/{name}/Chart.yaml）
    │
    ▼
4. 查询现有记录（product_key 判断新导入/再导入）
    │
    ├─ 新导入: 创建 delivery_product，status=NOT_DEPLOYED
    │   - params_json 初始化为 manifest 组件的 defaultValues
    │   - global_params_json = {}
    │
    └─ 再导入: 更新 delivery_product
        - 保留 params_json（新字段补默认值，消失字段删除）
        - 更新 package_version / manifest_json
    │
    ▼
5. 写入持久目录 data/delivery/{productId}/
    ├── package.zip (原包)
    └── extracted/ (解压版本)
    │
    ▼
6. 同步创建/更新 CloudService CR（若存在当前集群）
    - CR name: csvc-{productKey}
    - CR namespace: delivery-system
    - spec 从 manifest 构建
```

### 4.4 再导入时的参数合并

```python
def merge_params_on_reimport(new_manifest, old_params):
    """
    新导入包时合并参数：
    - 新组件的参数取 defaultValues
    - 已有组件的参数保留旧值
    - 已消失的组件参数删除
    """
    merged = {}
    for comp in new_manifest.components:
        if comp.name in old_params.get('overrides', {}):
            merged['overrides'][comp.name] = old_params['overrides'][comp.name]
        else:
            merged['overrides'][comp.name] = extract_defaults(comp)

    # 产品级参数：保留旧值，新参数补默认
    merged['params'] = old_params.get('params', {})
    for param in new_manifest.parameters:
        if param.name not in merged['params']:
            merged['params'][param.name] = param.defaultValue

    return merged
```

---

## 5. 参数管理

### 5.1 三层参数作用域

```
全局参数 (App.spec.globalParameters)
  ├── imageRegistry: "registry.example.com"
  ├── defaultStorageClass: "local-path"
  └── domainSuffix: "example.com"

产品级参数 (manifest.parameters[], 通过 targetComponents 分发)
  ├── replicaCount → 影响 backend, frontend
  └── logLevel → 影响所有组件

组件级参数 (manifest.components[].parameters[], 每个组件独立)
  ├── redis.replicaCount
  ├── redis.persistence.size
  └── backend.resources.limits.memory
```

### 5.2 表单 Schema 生成

**后端生成逻辑：** 从 CloudService.Spec 提取参数，组装为前端可渲染的 form schema。

```
后端从 CloudService.Spec 构建 form:
1. Group "global" — 固定三个字段（imageRegistry / defaultStorageClass / domainSuffix）
2. Group per component — 每个组件一个折叠面板
3. 每个字段含 path / label / type / default / required / validation

前端只渲染，不猜测字段。
```

**前端控件映射表：**

| `field.type` | 组件 | 备注 |
|---|---|---|
| `string` | `<Input>` (`shadcn/vue`) | 普通文本 |
| `integer` | `<Input type="number">` (`shadcn/vue`) | 整数输入，min/max 来自 validation |
| `number` | `<Input type="number">` (`shadcn/vue`) | 浮点数 |
| `boolean` | `<Switch>` (`shadcn/vue`) | 开关 |
| `select` | `<Select>` (`shadcn/vue`) | 下拉选择，options 来自 validation.enum |
| `password` | `<Input type="password">` (`shadcn/vue`) | 密码输入框 |

### 5.3 参数保存流程

```
用户修改参数 → 前端保存到 editedValues（本地状态）
                → 定时或手动点击"保存" → PUT /delivery/products/{id}
                → 后端写入 delivery_product.params_json
                → **不写入 K8s CR**（部署时才写入 App CR）
```

### 5.4 部署时的合并策略

部署时后端将用户参数合并后写入 App CR：

```
合并顺序（与 cn-app-operator MergeAll 一致）：
1. Chart 默认 values（来自 Helm chart）
2. 组件默认值（manifest.components[].defaultValues）
3. 产品级参数覆盖（manifest.parameters[], 按 targetComponents 匹配）
4. 用户填写的组件级覆盖（params_json.overrides）
5. 用户填写的全局参数（global_params_json）
6. 全局参数注入（imageRegistry → image.registry, defaultStorageClass → global.storageClass）
```

---

## 6. 集群管理

### 6.1 导入 kubeconfig

1. 校验 kubeconfig 格式（YAML 解析）
2. 解析 server 地址
3. 构建 client-go 客户端并探活（GET /version）
4. AES-256-GCM 加密存储
5. 若为第一个集群，自动设为当前集群（无 `tenant_id`，单租户模式）

### 6.2 "当前集群"概念

- 至多一个当前集群
- 浏览节点/SC/PVC 等库存信息打当前集群
- 首次部署打当前集群（冻结 `cluster_id`）
- 已部署产品绑定冻结的 `cluster_id`，不跟当前集群漂移

### 6.3 安全约束

- `kubeconfig_enc` 永不进 VO 对象、永不返回给前端
- 更新 kubeconfig：重新探活 → 成功则覆盖存储
- 删除集群：不可为当前集群，不可有已部署产品

---

## 7. 部署编排

### 7.1 部署状态机

```
                      ┌──────────────────────┐
                      │   NOT_DEPLOYED        │
                      │   (初始/卸载后)        │
                      └───────┬──────────────┘
                              │ 首次部署
                              ▼
                      ┌──────────────────────┐
                      │   DEPLOYING           │ ← 创建 App CR
                      └───────┬──────────────┘
                              │ cn-app-operator 调和
                              ▼
            ┌─────────────────┼──────────────────────┐
            ▼                 ▼                      ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
   │   READY      │  │  DEGRADED    │  │     FAILED       │
   │ (全部Ready)  │  │ (部分降级)    │  │ (有组件失败)     │
   └───────┬──────┘  └──────┬───────┘  └───────┬──────────┘
           │                │                   │
           └────────────────┼───────────────────┘
                            │ 升级/重试
                            ▼
                    ┌──────────────────┐
                    │   DEPLOYING      │ ← 更新 App CR
                    └──────────────────┘
                            │
                  ┌─────────┴──────────┐
                  ▼                    ▼
           ┌────────────┐     ┌──────────────┐
           │  READY     │     │   FAILED     │
           └────────────┘     └──────────────┘
                  │                    │ 卸载
                  ▼                    ▼
          ┌──────────────┐    ┌──────────────────┐
          │ UNINSTALLING │    │   UNINSTALLING   │ ← 删除 App CR
          └──────┬───────┘    └───────┬──────────┘
                 ▼                    ▼
         ┌──────────────┐    ┌──────────────────┐
         │ NOT_DEPLOYED │    │   NOT_DEPLOYED   │
         └──────────────┘    └──────────────────┘
```

### 7.2 部署流程（核心逻辑）

```
用户点击"部署" → POST /delivery/products/{id}/deploy
    │
    ▼
1. 前置校验（同步）
    ├── 存在可用集群（当前集群 或 已冻结 cluster_id）
    ├── 集群可达（k8s version 可获取）
    ├── 产品状态不是 DEPLOYING/UNINSTALLING
    └── 必填参数都有值
    │
    ▼
2. 冻结参数快照
    ├── 当前 params_json → deployment.params_snapshot_json
    ├── 当前 global_params_json → deployment.global_params_snapshot
    └── 当前 package_version → deployment.package_version
    │
    ▼
3. 创建/更新 App CR
    ├── 首次部署:
    │   ├── namespace: dlv-{productKey}
    │   ├── 确保 namespace 存在（带 tenant/product 标签）
    │   ├── 创建 App CR（带 tenant/product/managed-by 标签）
    │   └── 记录 deployment: action=DEPLOY, app_name, app_ns
    │
    └── 升级（已有 App CR）:
        ├── 更新 App CR spec（operator 自动触发 upgrade）
        └── 记录 deployment: action=UPGRADE
    │
    ▼
4. 更新产品状态
    ├── status = DEPLOYING
    ├── cluster_id = 当前集群（首次）/ 保留（已冻结）
    └── target_ns = dlv-{productKey}
    │
    ▼
5. 返回 deployment id → 前端开始轮询
```

### 7.3 App CR 创建示例

```yaml
apiVersion: delivery.hfwas.io/v1
kind: App
metadata:
  name: app-order-platform
  namespace: dlv-order-platform
  labels:
    delivery.hfwas.io/product: "order-platform"
spec:
  displayName: "订单服务平台"
  version: "1.2.0"
  globalParameters:
    imageRegistry: "registry.example.com"
    defaultStorageClass: "local-path"
    domainSuffix: "example.com"
  services:
    - name: "csvc-order-platform"      # 引用 CloudService
      alias: "order-platform"
      parameters:
        replicaCount: 3
        logLevel: "info"
  terminationPolicy: "Delete"          # 级联删除
```

### 7.4 状态轮询

```
部署后，前端 2s 轮询 GET /delivery/deployments/{deployId}
    │
    ▼
后端:
1. 读取 App CR status（client-go）
2. 映射 App.phase → 部署状态:
   Pending → PENDING
   Deploying → RUNNING
   Ready → SUCCEEDED
   Degraded → SUCCEEDED（但展示降级提示）
   Failed → FAILED
3. 若为终态，更新 delivery_product.status 和 delivery_deployment.status
```

### 7.5 回滚

```
POST /delivery/products/{id}/rollback
    │
    ▼
1. 查询该产品最近一次 SUCCEEDED 的 deployment（revision - 1）
2. 从参数快照恢复
3. 更新 App CR（operator 检测 chartVersion 差异 → rollback）
4. 创建 deployment: action=ROLLBACK
```

### 7.6 卸载

```
POST /delivery/products/{id}/uninstall
    │
    ▼
1. 删除 App CR（terminationPolicy=Delete → 级联删除 CloudComponent → Helm uninstall）
2. 更新 product.status = UNINSTALLING
3. operator 完成后 App CR 被删除 → 前端轮询检测到删除
4. product.status = NOT_DEPLOYED
5. **保留 namespace 和 product 记录**（可再次部署）
```

---

## 8. 审计追踪

### 8.1 本地审计（deployment 表）

每次部署/升级/回滚/卸载操作都写入 `delivery_deployment`：
- 完整的参数快照
- 操作人
- 操作时间
- 操作结果（SUCCEEDED / FAILED）

### 8.2 CRD 侧审计

cn-app-operator 在 `App.status.history` 和 `CloudService.status.history` 中记录操作历史：

```json
{
  "revision": 3,
  "version": "1.2.0",
  "operation": "Upgraded",
  "parameterSnapshot": { "replicaCount": 3 },
  "operator": "admin",
  "timestamp": "2026-09-22T12:00:00Z"
}
```

前端可直接从 App CR `status.history` 读取操作记录，无需额外审计表。

---

## 9. 前端设计

### 9.1 路由

```
/delivery                        → 重定向到 /delivery/products
├── /delivery/products           → 产品列表（卡片仪表盘 + 导入按钮）
│   ├── /delivery/products/:id   → 产品详情（参数表单 + 状态 + 操作）
│   └── /delivery/products/:id/deploy → 部署向导（3 步 wizard）
├── /delivery/clusters           → 集群列表（导入、设为当前、删除）
├── /delivery/clusters/:id       → 集群详情（库存：节点 / SC / PVC / Namespace）
├── /delivery/deployments/:id    → 部署详情/进度
```

已实现 `/delivery/products`、`/delivery/products/:id`、`/delivery/clusters`；其余为规划路由，见 §9.7。

### 9.2 产品注册

**当前实现是独立应用**：`delivery-platform/frontend` 自带 Vite 工程（独立 `index.html` / `main.ts` / 路由）与外壳，**未**接入控制台的产品注册表。因此下面这段注册代码尚未落地，仅作为后续并入控制台时的目标形态保留。

并入控制台时，在控制台产品注册表中追加：

```typescript
{
  key: 'delivery',
  name: '交付运维',
  description: '产品包导入、参数配置、部署与运维',
  icon: Package,
  path: '/delivery/products',
  group: '交付运维',
}
```

使用竖轨布局（类似 PipelineShell），不占用二级 Tab。

### 9.3 Vue 组件树

外壳是 `DeliveryShell.vue`，挂在 `/delivery` 的**父路由组件**上；`App.vue` 保持为纯 `<router-view />`。两者不可指向同一文件，否则外壳会嵌套渲染两层（见 §9.8）。

#### 已实现（v0.3，位于 `delivery-platform/frontend/src/`）

```
layouts/DeliveryShell.vue         竖轨外壳：220px 侧边栏（可折叠至 60px）+ 顶栏 + 当前集群指示
router/index.ts                   /delivery 挂 DeliveryShell + 三个子路由
App.vue                           仅 <router-view />，不含外壳
views/delivery/ProductList.vue    产品列表：统计卡 + 导入 / 搜索 / 刷新
views/delivery/ProductDetail.vue  产品详情：参数表单 + 操作
views/delivery/ClusterList.vue    集群列表 + 导入 kubeconfig
components/ui/                    Badge / Button / Card / Input / Select / Switch
api/delivery.ts                   全部 /api/delivery/** 调用
types/delivery.ts                 前端类型定义
```

#### 规划（目标结构，尚未创建）

```
DeliveryShell.vue (竖轨布局)
│
├── 左侧菜单:
│   ├── 集群总览     → /delivery/clusters
│   ├── 产品列表     → /delivery/products
│   └── 当前集群名展示在顶栏
│
├── DeliveryProductList.vue          (产品列表 + 卡片)
│   ├── ProductCard.vue              (单张产品卡片)
│   │   ├── StatusBadge.vue          (状态标记: Ready/Deploying/Failed)
│   │   └── AggregatedSummary.vue    (组件数统计小标签)
│   ├── ImportButton.vue             (导入按钮 + 文件选择)
│   └── ClusterSwitchBanner.vue      (当前集群切换提示条)
│
├── DeliveryProductDetail.vue        (产品详情页)
│   ├── ProductStatusHeader.vue      (顶部状态栏)
│   ├── ParamFormBuilder.vue         (自动生成的参数表单)
│   │   ├── FormGroup.vue            (分组折叠面板)
│   │   └── 字段组件 (由 type 驱动):
│   │       ├── StringField.vue
│   │       ├── IntegerField.vue
│   │       ├── BooleanField.vue
│   │       ├── SelectField.vue
│   │       └── PasswordField.vue
│   ├── ModuleToggleList.vue         (组件启用/禁用开关)
│   ├── ActionButtons.vue            (部署/升级/回滚/卸载按钮)
│   └── DeploymentHistory.vue        (部署历史列表)
│
├── DeliveryDeployWizard.vue         (部署向导, 3 步)
│   ├── StepClusterSelect.vue        (步骤 1: 选择目标集群)
│   ├── StepParamReview.vue          (步骤 2: 预览参数)
│   └── StepDeployConfirm.vue        (步骤 3: 确认并部署)
│
├── DeliveryDeployProgress.vue       (部署进度页)
│   ├── PhaseTimeline.vue            (阶段时间线)
│   └── ComponentStatusList.vue      (各组件状态列表)
│
├── DeliveryClusterList.vue          (集群列表页)
│   ├── ClusterImportDialog.vue      (导入 kubeconfig 弹窗)
│   └── ClusterRow.vue               (单行: 名称/地址/状态/当前标记)
│
├── DeliveryClusterDetail.vue        (集群详情/库存页)
│   ├── ClusterInfoCard.vue          (集群基本信息卡)
│   ├── NodeTable.vue                (节点表格)
│   ├── StorageClassTable.vue        (SC 表格)
│   ├── PvcTable.vue                 (PVC 表格, 可按 ns 过滤)
│   └── NamespaceTable.vue           (Namespace 表格)
```

### 9.4 产品卡片设计

```
┌──────────────────────────────────────────────┐
│ [状态徽章: READY]  订单服务平台   v2.1.0       │
│ 集群: k3d-dev  |  命名空间: dlv-order           │
│ ┌────────────────────────────────────────────┐│
│ │ ● 组件: 4/4   服务: 1/1  运行中 2 天       ││
│ │ ┌────────────────────────────────────────┐ ││
│ │ │ 组件名     状态    Helm版本    操作    │ ││
│ │ │ redis      ● Ready  17.3.0   [查看]  │ ││
│ │ │ postgres   ● Ready  13.2.0   [查看]  │ ││
│ │ │ backend    ● Ready  2.1.0    [查看]  │ ││
│ │ │ frontend   ● Ready  2.1.0    [查看]  │ ││
│ │ └────────────────────────────────────────┘ ││
│ └────────────────────────────────────────────┘│
│ [配置参数] [升级] [回滚] [卸载]        │
└──────────────────────────────────────────────┘
```

### 9.5 部署向导（3 步）

| 步骤 | 组件 | 说明 |
|------|------|------|
| 1. 选择集群 | `StepClusterSelect.vue` | 显示可用集群列表，选目标 |
| 2. 参数预览 | `StepParamReview.vue` | 展示合并后的参数（只读预览） |
| 3. 确认部署 | `StepDeployConfirm.vue` | 显示摘要，点击确认 → 跳转进度页 |

### 9.6 状态轮询

```typescript
// 部署进度页: 2s 轮询
const { pause } = useIntervalFn(async () => {
  const resp = await fetchDeployment(deployId)
  if (resp.status === 'SUCCEEDED' || resp.status === 'FAILED' || resp.status === 'CANCELLED') {
    pause()
    showResultNotification(resp.status)
  }
}, 2000)

// 产品详情页: 5s 轮询 App CR 状态
const { pause: pauseProductPoll } = useIntervalFn(async () => {
  const resp = await fetchProductDetail(productId)
  if (resp.phase === 'Ready' || resp.phase === 'Failed') {
    pauseProductPoll()
  }
}, 5000)
```

### 9.7 实现状态

后端 `delivery-platform/backend` 是独立 Go 模块（`github.com/hfwas/delivery-platform`，go 1.20），不是控制台后端的模块：

| 层 | 实际选型 |
|----|----------|
| HTTP | `go-chi/chi/v5` + `go-chi/cors` |
| K8s 客户端 | `k8s.io/client-go` v0.27.2、`sigs.k8s.io/controller-runtime` v0.15.0 |
| 持久化 | SQLite（`mattn/go-sqlite3`，WAL + 外键），`migrations/001_init.sql` 建 3 张表 |

`internal/api/handler.go` 已注册 §3 的 status / clusters（含 inventory）/ products（含 deploy、rollback、uninstall）/ deployments 各组路由。

与设计的偏差：

| 项 | 设计 | 现状 |
|----|------|------|
| kubeconfig 存储 | AES-256-GCM（§6.1、§6.3） | `encryptKubeconfig` 为 Base64 简化实现，源码自注「生产环境应使用 AES-256-GCM」 |
| 前端形态 | 并入控制台产品注册表（§9.2） | 独立 Vite 应用，未接入控制台 |
| Pod 日志 | `GET /delivery/resources/{apiVersion}/{kind}/{name}/logs`（§3.6） | 未注册 |

前端：§9.1 的三条路由与 §9.3「已实现」清单中的文件均已落地；部署向导、集群详情/库存页、部署进度页尚未实现。

### 9.8 外壳与样式约定

**外壳单例。** 外壳只允许出现在一条链路上：`App.vue` 作为根组件保持为纯 `<router-view />`，`DeliveryShell.vue` 只作为 `/delivery` 的父路由组件。曾因二者都指向 `App.vue`，导致侧边栏与顶栏整层嵌套、`<main>` 内出现 `100vh` 引发多余滚动条、`fetchStatus` 被重复调用。

**语义 token 必须显式声明。** 视图与 `components/ui/*` 按 shadcn 命名书写（`border-border`、`text-muted-foreground`、`text-primary-foreground`、`bg-secondary`、`ring-ring` 等）。Tailwind 对未定义的类名静默忽略、不报错，缺 token 只表现为颜色失效（如按钮文字压不住底色）。新增 token 需同时加进 `tailwind.config.js`。

**token 用字面量而非 `var()`。** Tailwind v3 对 `var()` 形式的颜色会丢弃 `/60`、`/50` 这类透明度修饰符（`text-muted-foreground/60` 会退化为完全不透明）。故 `tailwind.config.js` 直接写 hex，取值与 `src/style.css` 的 `--color-*` 保持一致。

**页面容器宽度统一为** `p-6 max-w-7xl mx-auto`，避免切换导航时内容左边缘横跳。

---

## 10. 与 cn-app-operator 集成

### 10.1 CRD 操作映射

| 用户操作 | 后端行为 | CRD 操作 | Operator 响应 |
|---------|---------|----------|--------------|
| 导入包 | 解析 manifest → 创建 CloudService CR | `CloudService[create]` | 注册产品蓝图 |
| 再导入 | 更新 CloudService CR | `CloudService[update]` | 检测变更 |
| 保存参数 | 写 delivery_product.params_json | 不写 K8s | 不触发（只是草稿） |
| 部署（首次） | 创建 App CR | `App[create]` | 创建 CloudComponent → Helm install |
| 升级 | 更新 App CR | `App[update]` | 检测 diff → Helm upgrade |
| 回滚 | 读取历史 deployment → 恢复参数 → 更新 App CR | `App[update]` | Operator 检测版本变化 → rollback |
| 卸载 | 删除 App CR | `App[delete]` | 级联删除 → Helm uninstall |
| 查看状态 | 读取 App CR status | `App[get]` | 返回聚合状态 |

### 10.2 本地存储 vs CRD 数据

| 数据 | 本地存储 (SQLite) | CRD (K8s) | 说明 |
|-----|------------------|----------|------|
| 集群 kubeconfig | `delivery_cluster.kubeconfig_enc` | 无 | 安全敏感，不写入 CR |
| 包文件 (zip) | 磁盘 `data/delivery/...` | 无 | 二进制大文件 |
| manifest 元数据 | `delivery_product.manifest_json` | `CloudService.Spec` | 双重存储防集群丢失 |
| 用户参数快照 | `delivery_product.params_json` | `App.Spec.services[].parameters` | 本地是草稿，CR 是生效值 |
| 部署记录 | `delivery_deployment` | App / CloudService `status.history[]` | 本地存快照，CR 存摘要 |
| 状态聚合 | `delivery_product.status` (摘要缓存) | `App.status` (权威) | 本地缓存，CR 是权威 |

### 10.3 标签约定

```yaml
metadata:
  labels:
    delivery.hfwas.io/product: "order-platform"      # 产品键
    delivery.hfwas.io/managed-by: "delivery-platform" # 管理方标识
```

### 10.4 错误处理

| 场景 | 错误处理 |
|------|---------|
| 集群不可达 | 部署前校验失败，返回 400 + 提示 |
| 创建 CR 冲突 | 捕获 K8s Conflict 异常，重试或提示用户 |
| App CR 调和失败 | 轮询检测 status.phase，展示 failureMessage |
| 组件级失败 | 读取 CloudComponent.status.failureMessage |
| 并发部署 | 产品 status 检查（DEPLOYING 时拒绝） |

---

## 11. 实现顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| **P1** | 后端数据模型（3 张 SQL 表：cluster / product / deployment） | 无 |
| **P1** | 集群管理 API（导入/设当前/列表/删除） + AES 加密 | delivery_cluster 表 |
| **P1** | 包导入 API（zip 解析/校验） | delivery_product 表 |
| **P2** | CloudService CR 同步（导入时创建/更新 CR） | P1 + client-go K8s 客户端 |
| **P2** | 部署 API（创建 App CR + 状态轮询） | P2 CR 客户端 |
| **P2** | 参数表单 API（schema 生成 + 保存） | P2 manifest 解析 |
| **P2** | 前端 DeliveryShell + 路由 + 产品列表卡片 | P2 API |
| **P2** | 前端产品详情 + 参数表单组件 | P2 API |
| **P2** | 前端部署向导 + 进度轮询 | P2 API |
| **P3** | 回滚 API（ControllerRevision 引用） | P2 部署 |
| **P3** | 前端集群库存页（节点/SC/PVC/Namespace） | P1 集群 |
| **P3** | 前端部署历史 + 组件 drill-down | P2 部署 |
| **P4** | 多集群切换增强 | P1-P3 |
| **P4** | 组件 workload 状态可视化、Pod 日志查看 | P2-P3 |

上表是分阶段计划，不代表当前进度；实际落地范围与偏差见 §9.7。

---

## 参考来源

| 来源 | 说明 |
|------|------|
| `docs/research/cn-app-operator-crd-design.md` | 三层 CRD 定义、参数 merge 策略、调和循环 |
| `docs/research/yunyou-operator-helm-package.md` | 能力中心白屏设计、产品卡片、工堪、参数表单模式 |
| `docs/superpowers/specs/2026-09-08-delivery-platform-design.md` | 旧 Tekton 方案的集群/产品/部署表设计、包导入流程 |
| `cn-app-operator/api/v1/` | App / CloudService / CloudComponent Go 类型定义 |
| `cn-app-operator/pkg/merge/values.go` | 6 步参数合并算法 |