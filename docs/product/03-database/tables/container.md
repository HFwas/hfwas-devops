# 容器平台数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/container-core/src/main/resources/db/container-schema.sql`、`backend/container-core/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：cluster_info、registry_info 字段与索引 |

---

容器平台只持久化“连接信息”；Pod、Deployment、Service、事件、监控、Helm Release 等均通过 Kubernetes/Prometheus/Helm API 实时获取，不落库。

## cluster_info

纳管的 Kubernetes 集群。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `tenant_id` | 所属租户 |
| `name` | 集群名称 |
| `alias` | 别名 |
| `provider` | 提供方（k3s/docker-desktop/其他） |
| `version` | K8s 版本 |
| `kubeconfig` | 集群凭证（敏感） |
| `mode` | 接入模式 |
| `status` | 连接状态 |
| `labels` | 标签 |
| `created_at` / `updated_at` | 创建/更新时间 |

索引：无显式索引。

## registry_info

镜像仓库连接信息（Harbor 等）。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `tenant_id` | 所属租户 |
| `name` / `alias` | 名称与别名 |
| `type` | 仓库类型 |
| `url` | 仓库地址 |
| `insecure` | 是否跳过 TLS 校验 |
| `credential_username` / `credential_password` | 凭证（敏感） |
| `source` | 来源 |
| `cluster_id` | 关联集群（可选） |
| `status` / `last_error` | 连接状态与最近错误 |
| `labels` | 标签 |
| `created_at` / `updated_at` | 时间 |

索引：无显式索引。

## 公共约定

- 主键使用 `AUTO_INCREMENT`（MyBatis-Plus `IdType.AUTO`）。
- 时间字段为 `created_at` / `updated_at`，无 `update_time`。
- **无逻辑删除字段**，删除即物理删除。
- 无 `create_by` / `update_by`，操作审计依赖 `sys_oper_log`（如已接入）。

## 写入方

| 表 | 归属 |
|----|------|
| `cluster_info` | `ClusterService`（`ClusterController`） |
| `registry_info` | `RegistryService`（`RegistryController`） |

## 安全提示

`cluster_info.kubeconfig` 与 `registry_info.credential_password` 属于敏感数据：

1. 接口返回时应脱敏（具体实现待确认）；
2. 数据库文件本身未加密，需依赖文件系统权限与备份加密；
3. 建议后续引入字段级加密或外部 Secret 存储。
