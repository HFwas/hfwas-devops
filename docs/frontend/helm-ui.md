# 容器产品 Helm 界面

> 日期：2026-10-07
> 版本：v0.1
> 关联：Helm 包上传与安装方案（Harbor OCI、禁止同版本覆盖）；交互参照 kite Helm 的目录、安装对话框与 Release 详情，视觉用本仓库冷蓝 token 与 `components/console`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：容器侧栏入口、页面流、以及后端未落地时的 API 客户端 |

---

## 1. 入口

只出现在容器管理侧栏「应用发布」，不进产品切换器。

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

`frontend/src/modules/container/api/helm.ts` 的 `HELM_USE_MOCK` 当前为 `true`。`origin/dev` 还没有 Helm Controller，页面使用会话内示例数据。类型与真实客户端一致，后端就绪后改为 `false`。

Chart 与仓库（不绑集群）：

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
