# 应用交付产品第一期设计

> 日期：2026-09-08  
> 状态：已拍板，待实施  
> 流水线对照：[2026-09-07-pipeline-design.md](./2026-09-07-pipeline-design.md)  
> 制品仓对照：[2026-09-08-artifact-registry-design.md](./2026-09-08-artifact-registry-design.md)（**本期交付不接 Harbor / 制品仓**）  
> CI 分层：[cicd-tech-selection.md](../../cicd-tech-selection.md)（交付层与流水线拆开；第一期不用 Argo CD）

绿野项目：直接按本文建表与 API，不做存量兼容。

---

## 1. 目标

控制台新产品 **应用交付**：导入 kubeconfig 作为 **当前集群**，查看节点 / 组件 / StorageClass / PVC / Namespace；导入部署包，改参数后部署到 **当前集群**（Tekton 与工作负载都在这套集群上）。一套产品只跑一套实例；升级 = 改参数后再部署。

Tekton 只跑安装作业。用户不写、不看 Tekton YAML / Helm 命令。**PVC、LB、库初始化是应用 Helm 的前置阶段**：PVC 对象已创建、LB 已有地址、库脚本跑完之后，才 `helm upgrade` 业务模块。应用 Chart 引用已有 PVC / 已注入的 LB 地址，不再自己建同名盘和同名 Service。PVC 不等 Bound 再往下走（`WaitForFirstConsumer` 要等消费 Pod）。

### 1.1 已拍板

| 项 | 决定 |
|----|------|
| 产品名 / key | 应用交付 / `delivery` |
| 控制台分组 | `交付运维`（不要塞进「资源编排」） |
| 包格式 | 自研 `manifest.json` + 包内 Helm Chart + hook 脚本；镜像不进包 |
| 产品与包 | 一个包 = 一个产品（可含多个模块）。导入后出现在产品列表 |
| 实例 | 租户内同一产品只允许一套在跑的实例 |
| 目标集群 | 租户 **导入 kubeconfig**；同一时刻一个 **当前集群**。浏览与 **新部署** 都打当前集群。已部署产品冻结 `cluster_id`，升级/卸载仍打那套，不跟当前集群漂移 |
| 执行 | 一条 TaskRun 提交到当前（或已冻结）集群；顺序：PVC → LB → 数据模块 → dbInit → 应用模块。该集群必须已装 Tekton |
| 镜像 | 页面填完整拉取地址（可含 registry/仓库/tag 或 digest）。**部署前不调 Harbor、不绑制品项目**。集群拉得到就继续，拉不到 Run 失败 |
| 制品依赖 | 本期 **不**依赖 `artifact-api`。**不**依赖 `pipeline-core` |
| 本地引擎 | 开发仍可用 compose profile `pipeline` 的 k3d；**在本产品里导入该集群 kubeconfig** 并设为当前。yml 里的 `pipeline.kubeconfig` 不再作为交付的隐式目标 |
| 多租户 | `X-Tenant-Id` + `CurrentUserAccessor`。kubeconfig 按租户隔离 |
| 参数 | 清单里所有可变字段都在详情页可改；没有「只读参数」。身份字段（key / chart 路径 / role）除外 |
| 权威数据 | 产品、参数、Run 在 SQLite。集群对象可删可按包重建 |

### 1.2 非目标（第一期）

- 多实例、把交付做成第二套「资源编排」产品（主机/中间件仍归资源编排占位）
- 一次部署拆到两套集群（Tekton 在 A、负载在 B）
- Argo CD / Flux、金丝雀、自研回滚引擎
- 部署前 Harbor / 制品仓校验、绑定制品项目、注入 Harbor robot pull Secret
- 包内带镜像 tar、把包存成 Harbor OCI（方案 B）
- 控制面直接 `helm template` + API apply（方案 C）
- 与部署脱钩、不在清单里的随意建盘 / 建 LB（PVC、LB 必须来自 manifest）
- 扩缩容表单、模块间依赖图
- 流水线 `DEPLOY` 任务对接本产品（继续只对执行集群 `kubectl apply -f k8s/`）
- 抽出共享 Tekton 客户端模块（两模块可各写提交/watch；以后再抽）

---

## 2. 架构

```
导入 kubeconfig → 设为当前集群 → 浏览节点/组件/SC/PVC/Namespace

导入 zip → 校验 manifest → 落盘 + delivery_product

点「部署」（默认当前集群）
  → 控制面：当前集群可达、已装 Tekton、必填参数、禁止并发
  → 冻结 product.cluster_id = 当前集群
  → 控制面：确保 namespace + Role/RoleBinding + 模块 values Secret
  → TaskRun 提交到该集群
  → Tekton：fetch → PVC → LB → inject-infra → helm-data → dbInit → helm-app → disable
```

| 层 | 做法 |
|----|------|
| 产品 | `CONSOLE_PRODUCTS` 追加 `delivery`，path `/delivery/clusters`，无 `comingSoon` |
| 前端 | `frontend/src/modules/delivery/`，`DeliveryShell` 竖轨（对齐 `PipelineShell`）。壳顶展示当前集群名 |
| 后端 | `delivery-core`，包名 `com.hfwas.devops.delivery`，经 `server` 入 classpath。第一期不拆 `delivery-api` |
| 无当前集群 / 不可达 | 库存页与部署返回可读错误；集群列表 / 产品列表仍可进 |

依赖方向（禁止反向）：

```
server → delivery-core
server → pipeline-core → artifact-api
delivery-core 不依赖 pipeline-core / artifact-api
pipeline-core 不依赖 delivery-core
artifact-core 不依赖 delivery-core
```

kubeconfig：**只**来自租户导入的 `delivery_cluster`（AES）。TaskRun 建在该集群的 `delivery.namespace`（默认 `hfwas-delivery`）。**工作负载**在产品自己的 namespace。控制面用同一份 kubeconfig 做库存查询与提交 TaskRun。

---

## 3. 部署包规范

导入物：zip 或 tar.gz，最大 **100MiB**。`manifest.json` 只允许两种位置：zip 根目录；或根下恰好一个目录，清单在该目录内。找到 0 份或多份、或目录再套一层才有清单 → 拒绝。根上同时有 `manifest.json` 和 `charts/` 是合法的。

```
order-suite-1.2.0/
  manifest.json
  charts/
    order-api/
    order-web/
  hooks/
    order-api-init.sql
```

### 3.1 `manifest.json`

`apiVersion` 必须是 `hfwas.delivery/v1`。未知顶层字段忽略。缺必填字段 → 拒绝导入。

| 字段 | 规则 |
|------|------|
| `key` | `^[a-z][a-z0-9-]{1,30}$`，写入 `product_key` |
| `version` | 非空字符串，最长 64；**不**强制 semver 解析 |
| `displayName` | 非空，最长 64 |
| `modules` | 至少 1 个；`key` 在包内唯一，同样 DNS 标签规则；**禁止** `pvcs` / `loadBalancers` / `images` / `dbInit` |
| `pvcs` | 可空。平台在应用 Helm **之前** apply，见 §3.3 |
| `loadBalancers` | 可空。平台在应用 Helm **之前** apply，见 §3.4 |

每个 module：

| 字段 | 规则 |
|------|------|
| `key` / `name` | `name` 展示用，最长 64 |
| `chart` | 相对包根的 Chart 目录，必须存在 `Chart.yaml` |
| `role` | `data`（库、中间件，先于 dbInit）或 `app`（业务）。默认 `app` |
| `enabled` | 默认 `true`（仅清单缺省；实例开关在 `modules_json`） |
| `images` | 至少 1 个（该模块要拉镜像才部署得起来） |
| `params` | 该模块希望运营改的 Helm values，**必须**全部列在这里。未列入的走 Chart 默认，页面没有入口 |
| `hooks.dbInit` | 可选；**在数据模块 Ready 之后、应用模块 Helm 之前**执行 |

`images[]`：

| 字段 | 规则 |
|------|------|
| `image` | 完整拉取地址，如 `docker.io/library/nginx:1.27`、`registry.example.com/order-api:1.2.0`、`repo@sha256:…`。导入时写入 values 默认值 |
| `valuesPrefix` | Helm values 前缀。默认 `image` |

**不是** Harbor 相对仓库名。页面上改的就是这一整串。集群（节点凭证 / 已有 imagePullSecret / 公有仓）能 pull 才算成功；平台部署前 **不**探活 Registry。

`params[]`：

| 字段 | 规则 |
|------|------|
| `name` | Helm values 点路径，如 `replicaCount`、`persistence.size` |
| `label` | 表单标签。缺省则用 `name` |
| `description` | 可空，表单帮助文案 |
| `type` | `string` / `int` / `bool` / `password` / `select` |
| `options` | `type=select` 时必填 `[{ label, value }]` |
| `default` | 可空；导入时若 `values_json` 尚无该路径则填入 |
| `required` | 默认 false。`true` 时部署前必须有值（default 也算） |

`hooks.dbInit`：

| 字段 | 规则 |
|------|------|
| `image` | 跑 hook 的容器镜像，完整拉取地址（如 `postgres:16`）。**页面可改**。同样不预校验，拉不到则该 step 失败 |
| `script` | 相对包根的文件，必须存在。**页面只读**（改脚本请再导入包） |
| `command` | 非空 shell 字符串；工作目录 = 解压根。**页面可改** |
| `when` | `install` 或 `always`。**页面可改** |

应用 / 数据 Chart **禁止**再创建与清单 `pvcs[].name`、`loadBalancers[].name` 同名的 PVC 或 Service。盘用 `existingClaim`（或等价 values）；LB 地址用平台注入的 `hfwas.delivery.lbs.*`（§5.4）。

### 3.3 `pvcs[]`

| 字段 | 规则 |
|------|------|
| `key` | 包内唯一，DNS 标签。values 覆盖走 `values_json.pvcs.{key}` |
| `name` | 集群里 PVC `metadata.name`，默认等于 `key` |
| `size` | 默认 `10Gi`；可被 values 覆盖 |
| `storageClassName` | 可空字符串 = 用集群默认 StorageClass |
| `accessModes` | 默认 `["ReadWriteOnce"]` |

平台生成 PVC YAML 并 apply。Step 只确认对象已存在（`kubectl get pvc`），**不等 `Bound`**：许多 StorageClass 是 `WaitForFirstConsumer`，没有消费 Pod 会永远 Pending，若在这里死等就轮不到后面的数据模块。盘真正 Bound 由后续 helm-data / helm-app 的 Pod Ready 保证。未声明 `pvcs` 则跳过整段。

### 3.4 `loadBalancers[]`

| 字段 | 规则 |
|------|------|
| `key` | 包内唯一，DNS 标签。values 走 `values_json.loadBalancers.{key}` |
| `name` | Service `metadata.name`，默认等于 `key` |
| `type` | `LoadBalancer`（默认）/ `NodePort` / `ClusterIP`。可被 values 覆盖 |
| `waitAddress` | 默认：`type=LoadBalancer` 为 true，其余 false。true 时等到 `status.loadBalancer.ingress[0]` 的 `ip` 或 `hostname`，超时 10m |
| `ports` | 至少 1 条 `{ name, port, targetPort, protocol }`，`protocol` 默认 TCP |
| `selector` | 非空 map。必须与后续 **app**（或 data）Pod 标签一致，由打包方保证 |
| `annotations` | 可空 |

开发 k3d 若没有 MetalLB / 云控制器，`LoadBalancer` 会一直等不到地址 → Run 失败，文案含 `未分配 ExternalIP，请改用 NodePort 或为集群安装 LB 控制器`。未声明则跳过整段。

库初始化连的是数据模块 Service 或外部 JDBC，不依赖应用 LB。LB 仍放在 dbInit **之前**，这样应用 Helm 时 overlay 里已经有地址，避免业务进程启动后再改环境变量。

### 3.5 页面可改 vs 只读

详情页按 GET 返回的 `form` 渲染，**不得**漏掉 schema 里的字段，也**不得**另开只能看不能改的参数区。

| 只读（身份 / 包结构） | 可改（写入 `values_json` / `modules_json`） |
|------|------|
| 产品 `key`、包 `version`、模块 `key` / `role` / `chart` | 产品 `displayName` |
| PVC / LB 的 `key` | 模块 `enabled` |
| dbInit `script` 路径 | 每个 image 一整串拉取地址 |
| | 每个 `params[]`（含 select） |
| | dbInit 的 `image`、`command`、`when` |
| | PVC：`name`、`size`、`storageClassName`、`accessModes` |
| | LB：`name`、`type`、`waitAddress`、`ports[]`、`selector`、`annotations` |

PVC / LB 的 `name` 在产品 `status` 为 `RUNNING` / `FAILED` / `DEPLOYING` 时禁止改（400：`卸载后才能改存储或 LB 名称`）。其余可改字段随时可保存，下次「部署」生效。

`values_json` 形状：

```json
{
  "pvcs": { "pg-data": { "name": "pg-data", "size": "20Gi", "storageClassName": "", "accessModes": ["ReadWriteOnce"] } },
  "loadBalancers": {
    "web": {
      "name": "order-web",
      "type": "NodePort",
      "waitAddress": false,
      "ports": [{ "name": "http", "port": 80, "targetPort": 8080, "protocol": "TCP" }],
      "selector": { "app.kubernetes.io/name": "order-web" },
      "annotations": {}
    }
  },
  "images": { "order-api": { "image": "registry.example.com/order-api:1.2.1" } },
  "dbInit": { "order-db": { "image": "postgres:16", "command": "psql ...", "when": "install" } },
  "order-api": { "replicaCount": 2 }
}
```

保留键：`pvcs`、`loadBalancers`、`images`、`dbInit`。模块 `key` 禁止与它们冲突。`images.{moduleKey}.{valuesPrefix}` 的值是 **字符串**（完整镜像地址），不是 `{repository,tag}` 对象。

导入时把清单默认值填进 `values_json`（已有路径不覆盖）。再导入时：新字段补 default；清单里消失的 PVC/LB/module/param 从 values 删掉。

### 3.2 再导入

同一租户同一 `key`：覆盖 `package_dir`、`manifest_json`、`package_version`。保留 `values_json`、`modules_json`、`namespace`。字段增删按 §3.5。

**不**自动对集群做 upgrade。要点「部署」。

---

## 4. 数据模型

包 zip 只落 `data/delivery/{tenantId}/{productId}/`（当前版本一份解压目录 + `package.zip`）。不把 blob 进 SQLite。kubeconfig 只进 SQLite AES 列，不落盘明文。

### 4.0 `delivery_cluster`

```sql
CREATE TABLE IF NOT EXISTS delivery_cluster (
    id                INTEGER      NOT NULL PRIMARY KEY,
    tenant_id         INTEGER      NOT NULL,
    name              TEXT         NOT NULL,
    server_host       TEXT,
    kubeconfig_enc    TEXT         NOT NULL,
    is_current        INTEGER      NOT NULL DEFAULT 0,
    deleted           INTEGER      NOT NULL DEFAULT 0,
    create_by         INTEGER,
    update_by         INTEGER,
    create_time       TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time       TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_name
    ON delivery_cluster (tenant_id, name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_current
    ON delivery_cluster (tenant_id) WHERE deleted = 0 AND is_current = 1;
```

| 列 | 规则 |
|----|------|
| `name` | 非空，最长 64，租户内未删除唯一 |
| `kubeconfig_enc` | AES（键 `delivery.credential-key`，空则回退 `pipeline.credential-key`）。API **永不**回传 |
| `server_host` | 导入时从 kubeconfig 解析 `clusters[].cluster.server`，只读展示 |
| `is_current` | 租户内至多一行 = 1。第一份导入自动为当前；之后用「设为当前」 |

导入：`multipart` 字段 `file`（或 JSON `{ name, kubeconfigText }`），最大 **1MiB**。必须是合法 kubeconfig（有 `clusters` / `users` / `contexts`）。使用文件里的 `current-context`；没有则取第一个 context。导入后立刻 `GET /version`：失败则整笔回滚，`kubeconfig 无法连接：{原因}`。**不**在导入时强制检测 Tekton（浏览库存可以；部署时再查）。

更新 kubeconfig：PUT 再传文件，再探活。名称可改。

删除：`is_current=1` → 400 `请先把当前集群改到其它集群`。若仍有产品 `cluster_id` 指向它且 `namespace` 非空 → 400 `请先卸载该集群上的产品`。软删。

库存查询（只读，打 **当前** 集群，不落库）：

| 视图 | 数据 |
|------|------|
| 节点 | `name, ready, roles[], kubeletVersion, osImage, allocatableCpu, allocatableMemory, unschedulable` |
| 组件 | `kube-system` 以及若存在的 `tekton-pipelines` 中 Deployment / DaemonSet / StatefulSet：`namespace, kind, name, ready, replicas, images[]`。外加 `kubernetesVersion`（`/version`） |
| StorageClass | `name, provisioner, reclaimPolicy, volumeBindingMode, allowVolumeExpansion, isDefault` |
| PVC | 可按 `namespace` 过滤；空则全部 ns。`namespace, name, phase, storageClassName, capacity, accessModes[], volumeName` |
| Namespace | `name, phase, creationTimestamp` |

组件不含 Node、不含任意业务 ns。不做 YAML 编辑、不做在库存页创建 PVC（创建仍走产品部署清单）。

### 4.1 `delivery_product`

```sql
CREATE TABLE IF NOT EXISTS delivery_product (
    id                   INTEGER      NOT NULL PRIMARY KEY,
    tenant_id            INTEGER      NOT NULL,
    product_key          TEXT         NOT NULL,
    display_name         TEXT         NOT NULL,
    package_version      TEXT         NOT NULL,
    package_dir          TEXT         NOT NULL,
    manifest_json        TEXT         NOT NULL,
    values_json          TEXT         NOT NULL DEFAULT '{}',
    modules_json         TEXT         NOT NULL DEFAULT '{}',
    cluster_id           INTEGER,
    namespace            TEXT,
    status               TEXT         NOT NULL DEFAULT 'NOT_DEPLOYED',
    deleted              INTEGER      NOT NULL DEFAULT 0,
    create_by            INTEGER,
    update_by            INTEGER,
    create_time          TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time          TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_product_key
    ON delivery_product (tenant_id, product_key) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_delivery_product_tenant
    ON delivery_product (tenant_id, deleted);
```

| 列 | 规则 |
|----|------|
| `values_json` | 形状见 §3.5。模块键不得与保留键冲突 |
| `modules_json` | `{ "<moduleKey>": { "enabled": true } }`；缺键视为 enabled |
| `cluster_id` | 首次 DEPLOY 时写成当时的当前集群，之后升级/卸载都用它。`NOT_DEPLOYED` 时可空 |
| `namespace` | 第一次 DEPLOY 时冻结：`dlv-t{tenantId}-{productKey}`，小写、非 `[a-z0-9-]` 改 `-`，连续 `-` 压缩，截断 63，不得以 `-` 结尾。之后不改 |
| `status` | `NOT_DEPLOYED` / `DEPLOYING` / `RUNNING` / `FAILED` / `UNINSTALLING` |

删除产品：若 `status` 为 `DEPLOYING` / `UNINSTALLING` → 400。若 `namespace` 列非空（集群里还可能有这套实例）→ 400，文案 `请先卸载再删除`。软删；磁盘包目录一并删。

### 4.2 `delivery_run` / `delivery_run_step`

```sql
CREATE TABLE IF NOT EXISTS delivery_run (
    id                 INTEGER      NOT NULL PRIMARY KEY,
    tenant_id          INTEGER      NOT NULL,
    product_id         INTEGER      NOT NULL,
    cluster_id         INTEGER      NOT NULL,
    action             TEXT         NOT NULL,
    package_version    TEXT         NOT NULL,
    values_snapshot    TEXT         NOT NULL,
    modules_snapshot   TEXT         NOT NULL,
    tekton_name        TEXT,
    package_token_hash TEXT,
    status             TEXT         NOT NULL,
    error_message      TEXT,
    started_at         TEXT,
    finished_at        TEXT,
    create_by          INTEGER,
    create_time        TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_run_product
    ON delivery_run (tenant_id, product_id, id DESC);

CREATE TABLE IF NOT EXISTS delivery_run_step (
    id           INTEGER NOT NULL PRIMARY KEY,
    run_id       INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    sort_order   INTEGER NOT NULL,
    status       TEXT    NOT NULL,
    log_text     TEXT,
    started_at   TEXT,
    finished_at  TEXT
);

CREATE INDEX IF NOT EXISTS idx_delivery_run_step_run ON delivery_run_step (run_id, sort_order);
```

| 列 | 规则 |
|----|------|
| `action` | `DEPLOY` / `UNINSTALL` |
| `cluster_id` | 本次作业使用的集群（与冻结的产品集群一致） |
| `cluster_id` | 本次作业使用的集群（与冻结的产品集群一致） |
| `status` | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED` |
| `package_token_hash` | fetch zip 用的一次性 token 的 SHA-256；明文只进 K8s Secret。API 不回传 |
| `log_text` | 每 step 上限 512KiB，超出截断并在末尾标明 |

不建 PVC / LB / Pod 表。

### 4.3 Helm 与命名空间

| 对象 | 规则 |
|------|------|
| Helm release | `{productKey}-{moduleKey}`，DNS 标签化，截断 53（Helm 63 再留余量） |
| 发现资源 | `kubectl` 列该产品 namespace 全部 Pod / PVC / Service / Ingress，不要求 Chart 打特定 label |
| 卸载 | Tekton 先 `helm uninstall` **app** release，再 **data** release；PVC 与 LB Service 不是 Helm release，随控制面删 namespace 一起删 |

该 ns 第一期只给这一个产品用。卸载时 ns 内非 Helm 对象一并随 ns 删除。

---

## 5. 部署流程与 Tekton

### 5.1 部署前（同步，失败不提交）

1. 存在 `is_current=1` 的集群，否则 `请先导入 kubeconfig 并设为当前集群`
2. 用该 kubeconfig（`NOT_DEPLOYED`）或产品已冻结的 `cluster_id`（升级/卸载）能 `GET /version`，否则 `集群不可达：{原因}`
3. 该 kubeconfig 对应集群已装 Tekton（能 list `tekton.dev/v1` Task），否则 `集群未安装 Tekton`
4. `delivery.package-base-url` 非空，否则 `未配置 delivery.package-base-url`
5. 每个 **enabled** 模块的每个 image：`values_json.images.{moduleKey}.{valuesPrefix}`（缺则清单 `image`）为非空字符串，否则 `请填写镜像地址：{moduleKey}`
6. dbInit 若会编进本次 Task：`values_json.dbInit.{moduleKey}.image`（缺则清单）非空
7. enabled 模块的 `required` params 有值
8. 产品 status 不是 `DEPLOYING` / `UNINSTALLING` → `产品正在部署` / `产品正在卸载`

`NOT_DEPLOYED` 的部署：`cluster_id` 写成当前集群。已冻结的产品忽略「当前」，避免切集群后把 Helm 打到另一套。

**不**请求制品仓、**不**预检 Registry。能否拉取只在集群跑起来之后见分晓。

通过后：`status=DEPLOYING`，插 `delivery_run`（冻结 version / values / modules / cluster_id）。

卸载前：用产品 `cluster_id` 探活；status 不是 `DEPLOYING`/`UNINSTALLING`；`namespace` 非空。`namespace` 为空 → `产品未部署`。卸载成功后清空 `namespace` 与 `cluster_id`。

### 5.2 控制面（用导入的 kubeconfig，不进 Tekton）

- 确保 `delivery.namespace` 存在，且其中有 ServiceAccount `hfwas-delivery`（TaskRun 用它跑）
- 无产品 `namespace` 则创建，标签：`hfwas.delivery/owned=true`、`hfwas.delivery/tenant={id}`、`hfwas.delivery/product={key}`
- 在产品 ns：Role（该 ns 内 `apiGroups: ["*"] resources: ["*"] verbs: ["*"]`）+ RoleBinding；subject 是 `delivery.namespace` 里的 SA `hfwas-delivery`（跨 ns RoleBinding，`subjects.namespace` 必填）
- **不**创建 Harbor docker-registry Secret，**不**改 default SA 的 imagePullSecrets。私有仓凭据靠集群已有配置（节点 / 事先打好的 pull secret）
- 每个 enabled 模块一份 values overlay（镜像地址 + 用户 params），挂到 Task 的 `/values/{moduleKey}.yaml`。**不要**在提交前写入 LB IP；地址由 inject-infra 在集群里现查
- 随机 package token → hash 入库，明文进作业 ns 的 Secret，供 fetch Bearer

### 5.3 Task 形状

一个 Task、一个 Pod、顺序 Step。作业 SA = `hfwas-delivery`。workspace 空目录即可（不强制 PVC；zip ≤ 100MiB）。

对象名：`dlv-{runId}`，DNS 标签。

镜像：

| Step | 镜像 |
|------|------|
| fetch / pvc / lb / inject-infra / helm / wait / disable / uninstall | `dtzar/helm-kubectl:3.16.4` |
| dbInit | 该模块 hook 的 `image` |

| Step | 行为 |
|------|------|
| fetch | `curl -fsSL -H "Authorization: Bearer $TOKEN" "$PACKAGE_URL" -o pkg.zip` 并解压到 workspace。URL = `{delivery.package-base-url}/internal/delivery/runs/{runId}/package`（**直连后端，不经 Kong**，与 Keycloak webhook 相同） |
| pvc | 按清单+values 生成 YAML，`kubectl apply -n {ns}`，确认 `get` 成功。**不等 Bound**（§3.3）。无 `pvcs` 则跳过 |
| lb | 生成 Service YAML 并 apply；`waitAddress=true` 时等到 ingress ip/hostname，超时 10m。无 `loadBalancers` 则跳过 |
| inject-infra | 查询已 apply 的 PVC 名与 LB 地址，写出 workspace `/values/_infra.yaml`（§5.4）。无 PVC/LB 则写空的 `hfwas.delivery` 对象 |
| helm-data | 每个 enabled `role=data`：`helm upgrade --install {release} {chart} -n {ns} -f /values/_infra.yaml -f /values/{moduleKey}.yaml --wait --timeout 10m`，再 `kubectl wait` 该 ns 中带 `hfwas.delivery/role=data` 的 Pod Ready（无此类 Pod 则跳过 wait） |
| dbInit | 工作目录为解压根；该模块 values + `_infra` 展平为 env（点路径 → 大写+下划线）；执行 `command`。**禁止**放到 helm-app 之后 |
| helm-app | 每个 enabled `role=app`：与 helm-data 相同的 `-f` 顺序 |
| disable | 本次 `enabled=false` 且 `helm status` 仍存在的 release：先卸 app 再卸 data |
| wait-app | `kubectl wait --for=condition=Ready pods --all -n {ns} --timeout=10m`（无 Pod 则跳过） |

Step 顺序固定为上表，缺段就省略该 Step，**不得**把 helm-app 提前。数据模块 Chart 应给 Pod 打标签 `hfwas.delivery/role=data`（打包约定）；没有该标签时 helm-data 的 `--wait` 仍以 Helm 自己的就绪为准，后面的 `kubectl wait` 按标签过滤，避免把尚未安装的 app Pod 算进来。

dbInit 编进 Task 当且仅当：该 enabled 模块有 `hooks.dbInit`，且（`when=always` **或** `when=install` 且本次算首次安装）。多个模块多个 dbInit step，按 modules 数组顺序，全部夹在 helm-data 与 helm-app 之间。

首次安装：不存在「晚于最近一次 `SUCCEEDED` `UNINSTALL`」的 `SUCCEEDED` `DEPLOY`。从未成功卸载过时，等价于「还没有任何 `SUCCEEDED` 的 `DEPLOY`」。点部署时把这个布尔冻结进编译，不在 Step 里查库。

卸载 Task：先 `helm uninstall` 快照中的 **app** release（忽略 not found），再 **data**；**不**在 Tekton 里删 PVC/LB/ns。Run `SUCCEEDED` 后控制面删 namespace、清空产品 `namespace` 列、`status=NOT_DEPLOYED`。

产品状态机：

| Run 结果 | 产品 status |
|----------|-------------|
| DEPLOY 提交 | `DEPLOYING` |
| DEPLOY `SUCCEEDED` | `RUNNING` |
| UNINSTALL 提交 | `UNINSTALLING` |
| UNINSTALL `SUCCEEDED` | `NOT_DEPLOYED` |
| 任一类 `FAILED` / `CANCELLED` | `FAILED`（卸载失败则 ns 仍在，须再卸） |

失败后 ns 与已安装资源保留，可再部署。不自动 rollback。取消 = 取消 TaskRun。

helm / wait / dbInit 失败时，控制面扫描产品 ns 的 Pod 与 Task step：出现 `ErrImagePull` 或 `ImagePullBackOff`（或 dbInit step 因拉镜像失败）→ `error_message` 为 `镜像拉取失败：{pod或step} {image}：{kubelet 原文}`。其它失败仍用 Tekton/Helm 原文。

### 5.4 values 合并

每个模块 helm 的 `-f` 顺序（后写覆盖先写）：

1. Chart 自带 `values.yaml`
2. `/values/_infra.yaml`（Task 内 **inject-infra** 生成，不是控制面预先算 LB IP）
3. 镜像：`values_json.images.{moduleKey}.{valuesPrefix}` 字符串，缺则清单。写入 overlay：
   - `{valuesPrefix}` = 完整地址（Chart 若用标量 `image:`）
   - `{valuesPrefix}.repository` / `{valuesPrefix}.tag` 或 `.digest` 按下面拆
4. 用户 `values_json[moduleKey]` 点路径展开

`/values/_infra.yaml` 形状钉死：

```yaml
hfwas:
  delivery:
    pvcs:
      pg-data:
        name: pg-data
        claimName: pg-data
    lbs:
      web:
        name: order-web
        ip: "203.0.113.10"   # 无则空字符串
        hostname: ""          # 有 hostname 无 ip 时填
        port: 80              # 该 LB 的 ports[0].port
```

拆分规则（只为兼容常见 Chart，页面仍然只填一串）：

- 含 `@`：`@` 前为 repository，`@` 后为 digest，不写 tag
- 否则取 **最后一个 `/` 之后** 的最后一个 `:`：前面整段为 repository，后面为 tag；若该段没有 `:`，repository = 整串，tag = `latest`

数据 / 应用 Chart 用 `{{ .Values.hfwas.delivery.pvcs.pg-data.claimName }}` 等引用。本期 **不**由平台注入 `imagePullSecrets`。

`{valuesPrefix}.pullPolicy` 不强制。模块 `params` 与镜像写入撞路径时，**params 覆盖镜像写入**。

### 5.5 配置

```yaml
delivery:
  credential-key: ${DELIVERY_CREDENTIAL_KEY:}
  namespace: ${DELIVERY_NAMESPACE:hfwas-delivery}
  package-base-url: ${DELIVERY_PACKAGE_BASE_URL:}
```

`credential-key` 空则回退 `pipeline.credential-key`。  
**没有** `delivery.kubeconfig`：目标只来自导入。  
`package-base-url`：**后端根 URL，不含 `/api`**。Task 拼 `/internal/delivery/runs/{runId}/package`。开发（k3d → 宿主机后端）`http://host.docker.internal:8089`；生产填后端 ClusterIP。空则部署失败。

compose **不**新增 profile。本地把 k3d kubeconfig 导入本产品即可。

---

## 6. 制品仓

本期交付 **不接** 制品仓：无 `artifact_project_id`、无 `ArtifactInspectPort`、无 Harbor robot、无部署前存在性校验。镜像能否用完全由集群 pull 决定。制品仓产品仍按它自己的规格做，与本模块无编译期依赖。

---

## 7. API

前缀 `/delivery`。均需登录。按 tenant 隔离。统一 `BaseResult`。参数错误 `BizException.of(ResultCode.BAD_REQUEST, 具体中文原因)`，不新增 ResultCode。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/delivery/status` | `{ currentClusterId, currentClusterName, cluster: "UP"\|"DOWN", kubernetesVersion, tekton: "UP"\|"DOWN", message }`。无当前集群：`cluster=DOWN`，`message=请先导入 kubeconfig` |
| GET | `/delivery/clusters` | 当前租户未删除列表。字段：id, name, serverHost, isCurrent, **无** kubeconfig |
| POST | `/delivery/clusters/import` | `multipart`：`name` + `file`；或 JSON `{ name, kubeconfigText }` |
| PUT | `/delivery/clusters/{id}` | `{ name? }` 或再传 kubeconfig 文件替换 |
| POST | `/delivery/clusters/{id}/current` | 设为当前（其它行 `is_current=0`） |
| DELETE | `/delivery/clusters/{id}` | 见 §4.0 |
| GET | `/delivery/cluster/nodes` | 当前集群节点 |
| GET | `/delivery/cluster/components` | 当前集群组件 + `kubernetesVersion` |
| GET | `/delivery/cluster/storage-classes` | 当前集群 SC |
| GET | `/delivery/cluster/persistent-volume-claims?namespace=` | 当前集群 PVC |
| GET | `/delivery/cluster/namespaces` | 当前集群 Namespace |
| POST | `/delivery/products/page` | `{ pageNo, pageSize, keyword }`。VO 含 `clusterId, clusterName` |
| POST | `/delivery/products/import` | `multipart/form-data` 字段 `file` |
| GET | `/delivery/products/{id}` | 含 `manifest` 摘要、`values`、`modules`、`form`、status、namespace、`clusterId`、`clusterName` |
| PUT | `/delivery/products/{id}` | `{ displayName?, values?, modules? }`。`values` 必须能被 `form` 对上；多出来的键 400。不能改 `product_key`。运行中改 PVC/LB `name` → 400 |
| DELETE | `/delivery/products/{id}` | 见 §4.1 |
| POST | `/delivery/products/{id}/deploy` | 新部署打 **当前集群**；升级打冻结集群 |
| POST | `/delivery/products/{id}/uninstall` | 打冻结集群 |
| GET | `/delivery/products/{id}/runs` | 列表，无日志 |
| GET | `/delivery/products/{id}/runs/{runId}` | 含 steps + 日志 |
| POST | `/delivery/products/{id}/runs/{runId}/cancel` | 取消 |
| GET | `/delivery/products/{id}/resources` | 产品 ns 的 Pod / PVC / Service / Ingress（走冻结集群） |
| GET | `/delivery/products/{id}/pods/{pod}/logs?container=&tailLines=500` | 应用日志 |

内部（Task fetch：**直连 backend:8089**，不经 Kong；`SecurityConfig` permitAll，Controller 校验 Bearer = package token，对齐 `/internal/keycloak/events`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/delivery/runs/{runId}/package` | 返回 zip。token 错或 run 非 `PENDING`/`RUNNING` → 401。成功不记审计明文 token |

VO 不含 `package_token_hash`、`package_dir`、`kubeconfig` / `kubeconfigEnc`。库存接口集群不可达时 HTTP 仍 200，body 空列表 + `message`（与制品仓 DOWN 时 images 空列表同一套路）。导入/设当前/部署失败仍 400。

---

## 8. 前端

### 8.1 路由与壳

- path：`/delivery/clusters`
- 模块：`frontend/src/modules/delivery/`
- `DeliveryShell`：左侧 `n-menu` 宽 168px；顶栏或壳上展示 **当前集群** 名称，可跳到集群列表切换
- `CONSOLE_TABS` 不加项

| 菜单 | 路由 | 页 |
|------|------|----|
| 集群 | `/delivery/clusters` | 导入 kubeconfig、设为当前、删除 |
| 节点 | `/delivery/cluster/nodes` | 当前集群节点表 |
| 组件 | `/delivery/cluster/components` | kube-system + Tekton 组件 |
| 存储类 | `/delivery/cluster/storage-classes` | StorageClass |
| PVC | `/delivery/cluster/pvcs` | 集群 PVC，可按 Namespace 筛 |
| Namespace | `/delivery/cluster/namespaces` | Namespace 列表 |
| 产品 | `/delivery/products` | 部署包列表 + 导入 |

产品内（竖轨仍高亮「产品」）：

| 路由 | 页 |
|------|----|
| `/delivery/products/:id` | 参数表单、模块开关、部署/卸载。未部署时文案：将部署到当前集群「{name}」；已部署：部署在「{冻结名}」 |
| `/delivery/products/:id/runs/:runId` | 作业 step 日志 |
| `/delivery/products/:id/resources` | 该产品 ns 的 Pod / PVC / Service / Ingress |

### 8.2 列表

页头「应用交付」+「导入」。表格：名称、key、包版本、部署集群、status、最近 Run、操作。无当前集群时页头提示去导入 kubeconfig，部署按钮仍可点，由后端拒绝。

### 8.3 详情

详情是唯一改参数的地方。左侧或顶部分组：**产品**、**存储**、**网络**、每个模块（含镜像地址与数据模块的库初始化）。

`GET` 的 `form` 是字段数组，前端只按它画控件，不自己猜清单：

```json
{
  "groups": [
    {
      "id": "product",
      "title": "产品",
      "fields": [
        { "path": "displayName", "label": "名称", "type": "string" }
      ]
    },
    {
      "id": "pvcs.pg-data",
      "title": "存储 / pg-data",
      "fields": [
        { "path": "values.pvcs.pg-data.size", "label": "容量", "type": "string", "required": true },
        { "path": "values.pvcs.pg-data.storageClassName", "label": "StorageClass", "type": "string" },
        { "path": "values.pvcs.pg-data.accessModes", "label": "访问模式", "type": "stringList" },
        { "path": "values.pvcs.pg-data.name", "label": "PVC 名", "type": "string", "locked": false }
      ]
    }
  ]
}
```

`locked: true` 时控件禁用（例如运行中的 PVC 名）。`type` 与 `params` 对齐，另加 `stringList`（accessModes）、`keyValue`（selector / annotations）、`portList`（LB ports）。

- 模块开关走 `modules.{key}.enabled`，`role` / `chart` 只读展示，不进 `form` 可写字段
- 镜像：每条一个输入框 `values.images.{module}.{valuesPrefix}`，type `string`，label「镜像地址」，placeholder 如 `registry.example.com/app:1.2.0`
- 模块 Helm 参数：每条 `params[]` 对应 `values.{moduleKey}.{name}`
- dbInit：`values.dbInit.{moduleKey}.image` / `command` / `when`；`script` 只读展示
- LB / PVC：§3.5 所列字段全部进 `form`
- 保存即 PUT；不改集群。点「部署」：**未部署**则打当前集群，**已冻结**则打原集群
- Run 页 step：pvc → lb → inject-infra → helm-data → dbInit → helm-app
- `password` 用密文输入框，GET 仍回发明文（§9）
- 库存页 2s 轮询；无当前集群展示空态 +「导入 kubeconfig」

页面上不允许出现「参数仅展示、去 YAML 里改」的入口。第一期不做自由 YAML 编辑器。

轮询：详情与 Run 页 2s，对齐流水线。

---

## 9. 安全

- kubeconfig AES 入库；VO / 日志 / 审计 **不**含明文。导入失败回滚，不留半行
- 包下载 token 只进 K8s Secret 与 hash 列；日志打码 Bearer
- values 里 `type=password` 以及 key 匹配 `(?i)password|secret|token` 的，写入 Run 日志时打码
- 内部 package 接口不走用户 JWT，token 绑单次 run
- 租户隔离：集群 / 产品 / run / 库存 / pod 日志均校验 tenant。库存只打当前集群；产品资源打冻结 `cluster_id`
- 目标 ns 的 Role 仅限该 ns；创建 ns 只用该集群导入的 kubeconfig
- 第一期 **不**对 `values_json` 做 AES。kubeconfig **要**加密

---

## 10. 测试

单测，不强制真集群：

- manifest 合法/拒绝（缺 apiVersion、非法 key、缺 Chart.yaml、缺 image、zip 结构）
- namespace / release 名 DNS 标签化与截断
- `form` 生成：§3.5 可改列每条都出现；`key` / `chart` / `role` / `script` 不出现在可写 fields
- PUT：缺 required、多未知键、运行中改 PVC/LB name → 400
- 点路径 → 嵌套 YAML；镜像字符串拆 repository/tag/digest
- 编译器：step 顺序 pvc → lb → inject-infra → helm-data → dbInit → helm-app；不得把 dbInit/helm-app 提前；缺清单段则省略对应 step
- 编译器：enabled → upgrade；disabled → uninstall（先 app 后 data）；`dbInit` 在 install/always 与首次安装组合
- Chart 同名 PVC/Service：导入时不查 Chart 内容；验收靠打包约定。单测只断言平台 apply 的 YAML `metadata.name`
- 非法 / 超大 kubeconfig 拒绝导入；租户内名称冲突 400
- 设当前：旧当前变为 0，新当前为 1
- 删除当前集群 / 仍有已部署产品占用 → 400
- 无当前集群 → 不提交 TaskRun；库存接口 200 空列表 + message
- 镜像地址为空 → 不提交 TaskRun
- 无 package-base-url → 拒绝部署
- 从失败 Pod 抽出 ImagePullBackOff → `error_message` 以 `镜像拉取失败：` 开头
- 并发：`DEPLOYING` 时第二次 deploy 400
- `NOT_DEPLOYED` 部署写入当前 `cluster_id`；已冻结产品升级不改 `cluster_id`

有 Tekton 的手工验收见 §11。

---

## 11. 验收

1. 产品目录进入「应用交付」落到集群页；未导入 kubeconfig 时可打开产品列表，部署失败原因含「请先导入」。
2. 导入合法 kubeconfig 后可设为当前；节点 / 组件 / SC / PVC / Namespace 能列出（连得上的集群）。
3. 导入合法 zip 后列表出现产品；非法 zip 无新行。
4. 再导入同一产品 key：版本与清单更新，旧 values 保留。
5. 导入后详情 `form` 含完整镜像地址、PVC 容量、LB 类型/端口、dbInit command、模块 params；改完保存再 GET 一致。
6. 无当前集群或镜像地址留空：不出现 TaskRun。填了错误镜像：TaskRun 会提交，失败后 `error_message` 含 `镜像拉取失败`。
7. 当前集群有 Tekton 且镜像能拉：一条 TaskRun 打在 **当前集群**；产品 `cluster_id` 冻结。step 顺序 fetch → pvc → lb → inject-infra → helm-data → dbInit → helm-app。
8. 切换当前集群后：库存页变成新集群；已部署产品的再部署 / 卸载仍打冻结集群。
9. 改参数再部署：Helm upgrade，同一 namespace、同一 `cluster_id`。RUNNING 时改 PVC 名被拒绝。
10. 关掉某 **app** 模块再部署：该 release uninstall，data / PVC / LB 仍在。
11. 卸载后 namespace 删除，产品 `NOT_DEPLOYED` 且 `cluster_id` 清空，再次部署打 **那时的** 当前集群。
12. 流水线模块行为不变；`pipeline-core` 与 `artifact-api` 均无对 `delivery` 的依赖。VO 永不出现 kubeconfig。
13. k3d 无 LB 控制器且 `type=LoadBalancer` 且 `waitAddress=true`：Run 失败，原因含 ExternalIP，不出现应用 Helm。

---

## 12. 实现时会动到的文件（清单，不是任务拆分）

- `backend/delivery-core/`（新模块）+ `backend/pom.xml` / `server` 依赖
- SQLite schema 启动脚本（与现有 pipeline/pm 同一套路）
- `frontend/src/modules/delivery/` + `frontend/src/shared/console/products.ts`
- `backend/server/.../SecurityConfig.java`：permitAll `/internal/delivery/runs/*/package`
- 用户侧 `/api/delivery/**` 已由 Kong `/api` 转到后端，**不必**为内部 package 改 `kong.yml`

不改 `pipeline-core` 的 `DEPLOY` kind。不改制品仓模块。
