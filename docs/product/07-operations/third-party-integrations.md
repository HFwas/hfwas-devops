# 第三方集成清单

> 日期：2026-10-19
> 版本：v0.2
> 状态：已完成
> 数据来源：`deploy/docker-compose.yml`、`deploy/charts/`、`backend/`、`docs/devops/`、`docs/container-platform/`、`docs/pipeline/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：认证、网关、K8s、CI、仓库、监控、分析等集成清单 |
| v0.2 | 2026-10-19 | 编排文件迁入 `deploy/`：更新 `docker-compose.yml`、`kong/` 路径引用 |

---

## 1. 集成总览

| 集成 | 用途 | 方向 | 配置/代码位置 | 状态 |
|------|------|------|---------------|------|
| Keycloak | 身份认证（OIDC/JWT） | 平台 → Keycloak | `deploy/docker-compose.yml`、`deploy/charts/keycloak`、`application.yml` | 已集成 |
| Keycloak SPI | 登录事件回传 | Keycloak → 平台 | `keycloak/`、`deploy/charts`、`/internal/keycloak/events` | 已集成 |
| Kong | API 网关（DB-less） | 浏览器 → 平台 | `deploy/kong/`、`deploy/charts/kong` | 已集成 |
| Kubernetes | 容器平台、流水线执行 | 平台 → K8s | `container-core`、`cluster_info`、`PIPELINE_KUBECONFIG` | 已集成 |
| Tekton | 流水线执行引擎 | 平台 → Tekton | `pipeline-core`、`scripts/install-tekton.sh` | 已集成 |
| Helm | 应用发布 | 平台 → Helm | `container-core` HelmReleaseController | 已集成 |
| Harbor | 镜像仓库 | 平台 → Harbor API | `registry_info`、`docs/container-platform/harbor-deployment.md` | 已集成 |
| Prometheus | 监控指标 | 平台 → Prometheus | `MonitorController`、`docs/container-platform/prometheus-deployment.md` | 已集成 |
| Dependency-Track | SBOM 漏洞分析 | 平台 ↔ Dependency-Track | `docs/pipeline/dependency-track-design.md`、`deploy/charts/dependency-track` | 部分集成（待确认开关） |
| GitLab | 代码托管 | 流水线 → GitLab | `pipeline.repo_url`、`scripts/sync-gitlab.sh`、`deploy/charts/gitlab` | 已集成（外部） |
| Argo Workflows | 通用工作流 | 平台 → Argo | `ArgoWorkflowController`、`deploy/charts/argo-workflows` | 后端已集成，前端未接入 |
| Argo Events / CD | 事件与持续部署 | 独立 | `deploy/charts/argo-events`、`argo-cd` | 平台未直接调用 |
| Python 生成/解析 | Docgen、OCR、文件处理 | 平台 → Python | `backend/scripts/`、`docs/docgen/`、`docs/file-parser/` | 已集成 |
| Elasticsearch / Loki 等 | 日志检索 | 待确认 | 未发现代码引用 | 未集成 |

## 2. 认证集成（Keycloak）

| 项目 | 说明 |
|------|------|
| Realm | `hfwas-devops` |
| 客户端 | 前端 SPA（`keycloak-js`） |
| 后端校验 | JWK Set URI；issuer 必须与 token `iss` 一致 |
| 用户同步 | JWT `sub` 映射 `sys_user.external_id` |
| 角色 | 平台角色存数据库，JWT 只用于身份认证 |
| 事件 | SPI 回调 `/internal/keycloak/events`，写入登录日志 |

参考：`docs/backend/keycloak-migration-plan.md`、`docs/devops/kong-local-dev-guide.md`。

## 3. Kubernetes 集成

| 项目 | 说明 |
|------|------|
| 连接方式 | kubeconfig 存储于 `cluster_info` |
| 资源范围 | Pod、Deployment、StatefulSet、Service、ConfigMap、Secret、PVC、StorageClass、Node、Namespace、Event |
| 操作方式 | 后端调用 K8s API；权限由 kubeconfig 对应的 ServiceAccount/RBAC 决定 |
| WebSocket | watch、logs、shell |
| 本地集群 | macOS k3s（Docker 容器）/ Windows docker-desktop |

## 4. 流水线集成（Tekton）

| 项目 | 说明 |
|------|------|
| 编译 | 平台流水线 → Tekton PipelineRun |
| 任务类型 | 数据库模板（`pipeline_task_kind`） |
| 凭证 | `pipeline_credential` → Tekton Secret |
| 产物 | Tekton 任务回传 `/pipeline/runs/{runId}/artifacts` |
| 命名空间 | `PIPELINE_NAMESPACE`（默认 `hfwas-pipeline`） |
| 参考 | `docs/pipeline/tekton-intro.md`、`docs/pipeline/pipeline-core-api.md` |

## 5. Harbor 集成

| 项目 | 说明 |
|------|------|
| 连接 | `registry_info.url` + 凭证 |
| 浏览 | project → repository → artifact |
| 扫描 | artifact scan / vulnerabilities |
| 部署 | `POST /container/registries/{registryId}/deploy` |
| 参考 | `docs/container-platform/registry-design.md`、`harbor-deployment.md` |

## 6. 监控集成（Prometheus）

| 项目 | 说明 |
|------|------|
| 查询 | 集群概览、节点、Pod、JVM 指标 |
| 配置 | 具体 Prometheus 地址与鉴权待确认 |
| 参考 | `docs/container-platform/prometheus-deployment.md`、`monitor-integration-design.md` |

## 7. 依赖分析集成

| 项目 | 说明 |
|------|------|
| SBOM | 流水线任务产出，回传平台 |
| 组件解析 | `dependency_component` |
| Dependency-Track | 上传 SBOM、查询漏洞（开关待确认） |
| 参考 | `docs/pipeline/dependency-analysis-design.md`、`dependency-track-design.md` |

## 8. 待确认

1. Dependency-Track 的启用开关、API Key 与项目映射。
2. Prometheus 数据源配置（单集群/多集群）。
3. 邮件/Webhook 通知渠道的实际供应商。
4. Argo 前端接入与产品归属。
5. Keycloak webhook secret 的配置项名称。
