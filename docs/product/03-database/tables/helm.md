# Helm 数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/container-core/src/main/resources/db/helm-chart-schema.sql`、`deploy/charts/backend/files/db/13-helm-chart-schema.sql`、`14-helm-chart-content.sql`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：Helm 仓库与制品表 |

---

Helm Release 本身由集群内 Helm 管理，不落库；平台只持久化 chart 仓库与上传的 chart 制品元数据/内容。

## helm_chart_repository

Helm chart 仓库。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `tenant_id` | 所属租户 |
| `name` | 仓库名称 |
| `type` | 仓库类型（OCI/HTTP 等） |
| `url` | 仓库地址 |
| `insecure` | 是否跳过 TLS 校验 |
| `credential_id` | 凭证 ID（关联流水线凭证，待确认） |
| `created_at` | 创建时间 |

索引：无显式索引。

## helm_chart_artifact

chart 制品/版本。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `repository_id` / `tenant_id` | 仓库与租户 |
| `chart_name` / `version` | chart 名称与版本 |
| `digest` | 内容摘要 |
| `size_bytes` | 大小 |
| `chart_ref` | chart 引用（仓库/名称/版本） |
| `description` / `app_version` / `keywords` | Chart.yaml 元数据 |
| `readme` | README 内容 |
| `values_yaml` | 默认 values |
| `content_cached` | 内容是否已缓存（14 号迁移新增） |
| `uploaded_by` | 上传人 |
| `created_at` | 创建时间 |

索引：`idx_helm_chart_artifact_tenant_name`。

## 公共约定

- 主键 `AUTOINCREMENT`。
- 时间字段 `created_at`，无 `updated_at`。
- 无逻辑删除字段。
- 无 `create_by`，上传人使用 `uploaded_by`。

## 写入方

| 表 | 归属 |
|----|------|
| `helm_chart_repository` | `HelmChartService` |
| `helm_chart_artifact` | `HelmChartService`（上传/缓存） |

## 注意

1. `credential_id` 指向哪个模块的凭证需确认（可能是 `pipeline_credential`）。
2. `content_cached` 与 chart 内容存储位置（DB 内还是文件系统）需确认。
