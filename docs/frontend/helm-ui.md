# 容器产品 Helm 界面

> 日期：2026-10-08
> 版本：v0.4
> 关联：Helm 包上传与安装方案（Harbor OCI、禁止同版本覆盖）；交互参照 kite Helm 的目录、安装对话框与 Release 详情，视觉用本仓库冷蓝 token 与 `components/console`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：容器侧栏入口、页面流、以及后端未落地时的 API 客户端 |
| v0.2 | 2026-10-07 | 侧栏只保留「应用」一组真实入口；记下与已落地 Chart API 的字段/路径差，mock 仍整页开启 |
| v0.3 | 2026-10-08 | 页面改走真实 API；补齐 Chart 详情/Values 与 Release 契约，字段以 `chartName`、`sizeBytes` 为准 |
| v0.4 | 2026-10-08 | Release 已存在/不存在使用固定文案；其它 helm 失败改为一行摘要；资源未写 namespace 时回落到 Release 命名空间 |

---

## 1. 入口

只出现在容器管理侧栏「应用」，不进产品切换器。合并后不再另挂「应用发布」，也不再使用集群路径上的 Helm 占位页。

| 菜单 | 路由 |
|------|------|
| Helm Release | `/container/helm/releases`、`/container/helm/releases/:namespace/:name` |
| Chart 目录 | `/container/helm/charts`、`/container/helm/charts/:repositoryId/:name` |
| 上传 Chart | `/container/helm/upload` |

`/container/helm` 重定向到 Release 列表。Release 使用顶栏当前集群。未选集群时页面提示先接入集群。只有把 `HELM_USE_MOCK` 改回 `true` 时，才会用 `demo-cluster` 走示例数据。

## 2. 页面流

- Chart 目录：搜索、版本与仓库，进入详情（概览 / Values / 版本 / README），从详情或某一版本打开安装对话框。
- 上传向导：选 OCI 仓库 → 选 `.tgz`（最大 50 MB）→ 展示 name、version、chartRef。同仓库同版本返回重复错误，不覆盖。
- 安装对话框：Release 名、命名空间、只读默认 Values、可编辑自定义 Values、试运行清单、安装。成功后进入 Release 详情。
- Release 列表与详情：概览 / Values / 资源 / 历史 / 日志 / 清单。升级抽屉可选版本，并在「保留自定义 Values」和「重置为该版本默认 Values」之间选择，再试运行后升级。历史里可查看 Values 并回滚到指定 revision（回滚生成新 revision）。卸载需确认。

日志页目前只放共享 `LogPanel` 空态。Pod 日志聚合留给后端。

## 3. API 契约

`frontend/src/modules/container/api/helm.ts` 的 `HELM_USE_MOCK` 为 `false`。页面走 `helmHttp`。`helmMock` 留给单测，以及本地没有后端时手动把开关改回 `true`。

Kong 仍剥掉 `/api`。`/container/**` 需要登录。Long 主键经全局 Jackson 写成 JSON 字符串，前端 id 保持 `string`。

前端字段跟随后端：摘要和详情用 `chartName`，制品大小用 `sizeBytes`。列表和详情都带 `repositoryName`。`valuesYaml` 是 YAML 文本。安装引用 `chartRef` 或 `artifactId`，不把本地 tgz 直接装进集群。`valuesStrategy`（`keep` / `reset`）只给前端决定提交哪份 YAML，后端按收到的 `valuesYaml` 执行。

### 3.1 Chart（不绑集群）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/container/helm/repositories` | `{ id, name, type, url, insecure, createdAt }` |
| GET | `/container/helm/charts?repositoryId&name` | 摘要列表。`name` 是对 chart 名、说明、仓库名的大小写不敏感包含匹配 |
| GET | `/container/helm/charts/{name}?repositoryId&version` | 详情。省略 `version` 时取最新 SemVer。多个仓库都有该 Chart 且未给 `repositoryId` 时返回 30301 |
| GET | `/container/helm/charts/{name}/versions?repositoryId` | 制品列表 |
| GET | `/container/helm/charts/{name}/versions/{version}?repositoryId` | 单个制品 |
| GET | `/container/helm/charts/{name}/versions/{version}/values?repositoryId` | `{ valuesYaml }`，chart 包里的默认 values |
| POST | `/container/helm/charts/upload` | multipart `file`，可选 `repositoryId`。同名同版本 HTTP 409，业务码 30303 |

摘要：`repositoryId`、`repositoryName`、`chartName`、`description`、`latestVersion`、`appVersion`、`versionCount`、`updatedAt`。

详情在摘要字段之外还有当前 `version`、`appVersion`、`chartRef`、`artifactId`、`keywords`、`readme`，以及 `versions[]`（`artifactId`、`version`、`appVersion`、`chartRef`、`digest`、`createdAt`）。

制品：`id`、`repositoryId`、`repositoryName`、`chartName`、`version`、`appVersion`、`description`、`digest`、`sizeBytes`、`chartRef`、`uploadedBy`、`createdAt`。`sizeBytes` 在 JSON 里是字符串。

上传时从包里抽出 keywords、README.md、values.yaml，写入 `helm_chart_artifact` 并标记 `content_cached`。之后读详情或默认 Values 直接用库里的文本。`content_cached` 为假时，后端用配置的 OCI 凭据 `helm pull` 一次并回写。空的 values 只要已缓存就不会再拉。

### 3.2 Release（绑当前集群）

前缀 `/container/clusters/{clusterId}/helm/releases`。集群必须是 `Connected`，kubeconfig 用现有集群存储解密，只放进当次命令的临时目录，命令结束即删。安装从 Harbor OCI `chartRef` 拉取，需要登录时密码走 stdin，不进 argv、日志或响应。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `?namespace` | 列表。不含 values、清单和历史 |
| GET | `/{namespace}/{name}` | 详情：用户 values、清单、资源、历史（每个 revision 的 values） |
| GET | `/{namespace}/{name}/history` | 历史 |
| GET | `/{namespace}/{name}/values` | `{ valuesYaml }` |
| GET | `/{namespace}/{name}/manifest` | `{ manifest }` |
| GET | `/{namespace}/{name}/resources` | 从清单解析出的资源，状态为 `unknown`。模板未写 `metadata.namespace` 时用该 Release 的命名空间 |
| POST | `/{namespace}` | 安装 |
| POST | `/{namespace}/dry-run` | 安装试运行 |
| PUT | `/{namespace}/{name}/upgrade` | 升级 |
| PUT | `/{namespace}/{name}/upgrade/dry-run` | 升级试运行 |
| PUT | `/{namespace}/{name}/rollback` | body `{ revision }` |
| DELETE | `/{namespace}/{name}` | 卸载 |

安装 body：`name`、`chartRef` 和/或 `artifactId`、`valuesYaml`、`createNamespace`、`wait`。升级 body 再加 `version`（须与 `chartRef` 的 tag 一致）、`valuesStrategy`、`rollbackOnFailure`。空 `valuesYaml` 表示用 chart 默认值，不传 `--values`。试运行返回 `{ manifest, resources }`。`rollbackOnFailure` 对应 `helm upgrade --atomic`；否则在 `wait` 为真时加 `--wait`。

Release 对象：`clusterId`、`namespace`、`name`、`chartName`、`chartVersion`、`appVersion`、`chartRef`、`repositoryId`、`artifactId`、`status`、`revision`、`valuesYaml`、`notes`、`manifest`、`resources`、`history`、`updatedAt`。只有租户内恰好一条制品的 chart 名和版本对得上时，才会填 `repositoryId` / `artifactId`。

### 3.3 错误与超时

| 情况 | HTTP | 业务码 |
|------|------|--------|
| Chart 同名同版本 | 409 | 30303 |
| Release 已存在（`msg` 固定为「Helm Release 已存在」） | 409 | 30310 |
| Release 不存在（`msg` 固定为「Helm Release 不存在」） | 404 | 30309 |
| helm 失败（一行摘要，已去掉口令、kubeconfig 和 pull/digest 噪声） | 200 | 30311 |
| Chart 拉取失败 | 200 | 30312 |
| 参数无效（名称、values 不是对象、version 与 chartRef 不一致） | 200 | 30313 |
| Chart 不存在 / 多个仓库未指定 repositoryId | 200 | 30306 / 30301 |
| 集群未连接 | 200 | 30006 |

页面用响应里的 `msg` 展示上传冲突、试运行、安装、升级、回滚和卸载失败。

后端单次查询超时 60 秒，安装/升级/回滚/卸载 180 秒，`--wait` 或 `--atomic` 600 秒。前端在此之上为安装、升级和回滚多留 120 秒给详情回读；未缓存 Chart 的详情和默认 Values 用 300 秒，以盖住登录加 `helm pull`。

helm 进程包在 `HelmProcessRunner` 后面，单测不需要集群或 Harbor。后端镜像已安装 `helm` CLI，`HELM_BINARY_PATH` 可覆盖。超时默认在 `application.yml`，可用 `HELM_RELEASE_TIMEOUT_SECONDS`、`HELM_RELEASE_WAIT_TIMEOUT_SECONDS`、`HELM_RELEASE_QUERY_TIMEOUT_SECONDS` 覆盖。OCI 仓库仍由 `HELM_CHART_OCI_*` 与 Secret `helm-oci-password` 配置。
