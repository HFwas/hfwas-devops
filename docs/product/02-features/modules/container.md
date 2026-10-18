# 容器平台功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/container-core/`、`frontend/src/modules/container/`、`docs/container-platform/`、`README.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：多集群管理、工作负载、监控、镜像仓库、Helm |

---

## 1. 概述

容器平台提供多 Kubernetes 集群统一管理：纳管集群、浏览与操作核心资源、查看监控与日志、通过 Web 终端进入 Pod、管理镜像仓库与 Helm chart/release。K8s 资源通过集群 API 实时读取，平台只持久化集群与仓库连接信息。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 平台管理员 | 纳管集群、配置镜像仓库与 Helm 仓库 |
| 研发工程师 | 查看 Pod/日志、编辑 Deployment、上传/下载 Pod 文件 |
| SRE | Helm 安装/升级/回滚、扩缩容、重启、监控排障 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 集群管理 | 分页、纳管、详情、编辑、删除、连接测试、统计、组件 | `/container/clusters` | 可用 |
| 命名空间 | 列表 | `/clusters/:id/namespaces` | 可用 |
| 节点 | 列表、详情 | `/clusters/:id/nodes` | 可用 |
| Pod | 列表、详情、YAML、日志、删除 | `/clusters/:id/pods` | 可用 |
| Pod 终端 | Web Shell | Pod 详情 | 可用 |
| Pod 文件 | 上传、下载 | Pod 详情 | 可用 |
| Deployment | 列表/详情、Pod、环境变量、卷、YAML、扩缩容、重启、删除 | `/clusters/:id/deployments` | 可用 |
| StatefulSet | 列表/详情、Pod、环境变量、卷、YAML、删除 | `/clusters/:id/statefulsets` | 可用 |
| Service | 列表/详情/YAML | `/clusters/:id/services` | 可用 |
| ConfigMap | 列表/YAML 编辑/删除 | `/clusters/:id/configmaps` | 可用 |
| Secret | 列表/删除 | `/clusters/:id/secrets` | 可用 |
| PVC / StorageClass | 列表、删除 PVC | `/clusters/:id/pvcs`、`/storageclasses` | 可用 |
| 监控 | 集群概览、节点/Pod 指标、JVM 指标 | 资源详情 | 可用 |
| 事件 | 命名空间事件查询 | `/clusters/:id/events` | 后端可用、前端占位 |
| 镜像仓库 | 仓库 CRUD、连接测试、project/repo/artifact 浏览、删除、漏洞扫描 | `/container/registries` | 可用 |
| 镜像搜索 | 跨仓库搜索 | `/container/images` | 可用 |
| Helm 仓库/Chart | 仓库列表、上传、版本、values、readme | `/container/helm/charts`、`/upload` | 可用 |
| Helm Release | 列表/详情、dry-run、安装、升级、回滚、历史、卸载 | `/container/helm/releases` | 可用 |
| 其他资源 | DaemonSet/Job/CronJob/Ingress/HPA/CRD 等 16 类 | 占位页 | 占位 |

## 4. 核心流程

### 4.1 纳管集群

```text
管理员提交 kubeconfig 与集群信息
  → 校验 kubeconfig 可连接
  → 写入 cluster_info（kubeconfig 加密/受限存储，待确认）
  → 后续按 clusterId 构建 K8s client
```

### 4.2 工作负载操作

```text
选择集群 → 命名空间 → 资源列表
  → 详情页读取 YAML / 环境变量 / 卷
  → 修改 YAML、环境变量或卷 → PUT 提交
  → 直接调用 K8s API 更新
  → 操作写入操作日志（若已接入）
```

### 4.3 Helm 发布

```text
上传 chart 或引用仓库 chart
  → dry-run 渲染
  → 安装/升级到目标命名空间
  → 查看 history/values/manifest/resources
  → 回滚到指定 revision
```

### 4.4 Pod 终端与文件

```text
WebSocket /ws/container/shell/** 建立连接
  → 后端通过 K8s exec 转发
  → 文件上传/下载通过 PodFileController 转发到 Pod 文件系统
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 租户隔离 | `cluster_info`、`registry_info`、`helm_chart_repository`、`helm_chart_artifact` 带 `tenant_id` |
| 集群状态 | `status` 记录连接状态，`POST /{id}/test` 主动探测 |
| 镜像仓库凭证 | `credential_username` / `credential_password` 存储，接口是否脱敏待确认 |
| Helm 制品缓存 | `content_cached` 标记 chart 内容是否已缓存 |
| 删除保护 | 资源删除直接调用 K8s API，无二次确认由前端负责 |
| 集群配置存储 | 暂无逻辑删除字段，删除即物理删除 |
| 监控数据源 | 通过 Prometheus 查询，具体地址与鉴权待确认 |

## 6. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| kubeconfig 无效 | 连接测试失败，返回错误信息 |
| 集群不可达 | 列表/详情返回错误，`last_error` 记录镜像仓库错误 |
| 资源不存在 | 返回 `10003 NOT_FOUND` 或 K8s 原始错误 |
| 删除 namespace 下资源 | 直接调用 K8s，权限不足由 K8s RBAC 拒绝 |
| WebSocket 断开 | 前端重连，后端释放 exec 会话 |
| 镜像仓库扫描无报告 | 返回空/提示尚未扫描 |

## 7. 接口与数据

- 接口：`/container/clusters/*`、`/container/registries/*`、`/container/images/*`、`/container/helm/*`、`/argo/*`
- WebSocket：`/ws/container/watch/**`、`/ws/container/logs/**`、`/ws/container/shell/**`
- 详见 [04-api/modules/container.md](../../04-api/modules/container.md)
- 数据表：`cluster_info`、`registry_info`、`helm_chart_repository`、`helm_chart_artifact`
- 详见 [03-database/tables/container.md](../../03-database/tables/container.md) 与 [helm.md](../../03-database/tables/helm.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-CON-01 | 可纳管 k3s 与 Docker Desktop 集群并正确读取资源 |
| AC-CON-02 | Pod 日志可流式查看，终端可执行命令 |
| AC-CON-03 | Deployment 扩缩容/重启/YAML 修改生效 |
| AC-CON-04 | Helm dry-run/安装/升级/回滚结果与 Helm CLI 一致 |
| AC-CON-05 | 镜像仓库可浏览 project/repo/artifact 并展示扫描漏洞 |
| AC-CON-06 | 跨租户无法看到其他租户集群 |

## 9. 待确认

1. kubeconfig 与镜像仓库密码的加密/脱敏方式。
2. Prometheus 数据源配置与多集群监控接入方式。
3. 16 类占位资源的上线优先级。
4. Argo 工作流是否属于容器平台产品范围。
