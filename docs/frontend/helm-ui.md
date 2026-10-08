# 容器产品 Helm 界面

> 日期：2026-10-07
> 版本：v0.2
> 关联：Helm 包上传与安装方案（Harbor OCI、禁止同版本覆盖）；交互参照 kite Helm 的目录、安装对话框与 Release 详情，视觉用本仓库冷蓝 token 与 `components/console`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：容器侧栏入口、页面流、以及后端未落地时的 API 客户端 |
| v0.2 | 2026-10-07 | 侧栏只保留「应用」一组真实入口；记下与已落地 Chart API 的字段/路径差，mock 仍整页开启 |

---

## 1. 入口

只出现在容器管理侧栏「应用」，不进产品切换器。合并后不再另挂「应用发布」，也不再使用集群路径上的 Helm 占位页。

| 菜单 | 路由 |
|------|------|
| Helm Release | `/container/helm/releases`、`/container/helm/releases/:namespace/:name` |
| Chart 目录 | `/container/helm/charts`、`/container/helm/charts/:repositoryId/:name` |
| 上传 Chart | `/container/helm/upload` |

`/container/helm` 重定向到 Release 列表。Release 使用顶栏当前集群；未选集群且仍在示例数据模式时，页面用 `demo-cluster` 以便先走通流程。

## 2. 页面流

- Chart 目录：搜索、版本与仓库，进入详情（概览 / Values / 版本 / README），从详情或某一版本打开安装对话框。
- 上传向导：选 OCI 仓库 → 选 `.tgz`（最大 50 MB）→ 展示 name、version、chartRef。同仓库同版本返回重复错误，不覆盖。
- 安装对话框：Release 名、命名空间、只读默认 Values、可编辑自定义 Values、试运行清单、安装。成功后进入 Release 详情。
- Release 列表与详情：概览 / Values / 资源 / 历史 / 日志 / 清单。升级抽屉可选版本，并在「保留自定义 Values」和「重置为该版本默认 Values」之间选择，再试运行后升级。历史里可查看 Values 并回滚到指定 revision（回滚生成新 revision）。卸载需确认。

日志页目前只放共享 `LogPanel` 空态。Pod 日志聚合留给后端。

## 3. API 客户端

`frontend/src/modules/container/api/helm.ts` 的 `HELM_USE_MOCK` 仍为 `true`，目录、上传和 Release 共用这一开关。页面使用会话内示例数据。

没有按能力拆开：Chart 列表和上传虽然已有 Controller，但详情与 values 还不能由现有接口填满，安装/升级也还没有。把开关改成 `false` 会让目录页和安装页一起打到未对齐的接口。

Chart 与仓库（前端客户端，不绑集群）：

| 方法 | 路径 |
|------|------|
| GET | `/container/helm/repositories` |
| GET | `/container/helm/charts` |
| GET | `/container/helm/charts/{name}?repositoryId&version` |
| GET | `/container/helm/charts/{name}/versions/{version}/values?repositoryId` |
| POST | `/container/helm/charts/upload`（`file`、`repositoryId`） |

Release（绑集群）：

| 方法 | 路径 |
|------|------|
| GET | `/container/clusters/{clusterId}/helm/releases` |
| GET / DELETE | `/container/clusters/{clusterId}/helm/releases/{namespace}/{name}` |
| POST | `…/releases/{namespace}` 安装 |
| POST | `…/releases/{namespace}/dry-run` |
| PUT | `…/releases/{namespace}/{name}/upgrade` 与 `…/upgrade/dry-run` |
| PUT | `…/releases/{namespace}/{name}/rollback`，body `{ revision }` |

请求里的 `valuesYaml` 是 YAML 文本，对应方案中的 values，由后端解析。安装引用 `chartRef` 或 `artifactId`，不把本地 tgz 直接装进集群。

### 3.1 与已落地后端的差异

`HelmChartController`（`/container/helm`）已实现上传、仓库、列表和版本查询。Kong 仍是 `/api` 前缀剥离，安全配置里 `/container/**` 需要登录，与其它容器接口相同。multipart 字段名 `file`、`repositoryId` 一致；`repositoryId` 后端是可选的 Long。

| 前端 `helmHttp` | 后端现状 |
|------|------|
| `GET /charts` 摘要含 `name`、`repositoryName` | 同路径。字段是 `chartName`，没有 `repositoryName` |
| `GET /charts/{name}?repositoryId&version` 返回详情（`readme`、`keywords`、`versions`） | 没有该路径。已有 `GET /charts/{name}/versions` 和 `GET /charts/{name}/versions/{version}`，返回制品，不含 README |
| `GET /charts/{name}/versions/{version}/values` | 未实现 |
| 制品 `size`、`repositoryName`；id 为 string | 制品 `sizeBytes`，无 `repositoryName`；id 为 Long。多 `uploadedBy` |
| 仓库 `id: string`，`type: 'oci'` | `id` 为 Long，多 `insecure` |
| Release 全部路径 | 未实现（P1） |

因此目录/上传不能单独切到真实客户端，同时让安装对话框继续用 mock 的 values。后端镜像已安装 `helm` CLI，`HELM_BINARY_PATH` 可覆盖；Chart 的 `HELM_CHART_OCI_*` 在 `application.yml` 与 backend Helm values 里是空占位，口令只进 Secret。
