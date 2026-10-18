# 接口模块覆盖矩阵

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成
> 数据来源：`grep @RequestMapping` / `@*Mapping`（排除 `target/`）、`docs/backend/api_spec.md`、`docs/pm/pm-api.md`、`docs/pipeline/pipeline-core-api.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：8 个模块、67 个 Controller 的文档与 Postman 覆盖矩阵 |
| v0.2 | 2026-10-18 | 流水线 Markdown 升 v0.4 并补齐 Postman；更新覆盖状态 |
| v0.3 | 2026-10-18 | P0：PM/容器/API 测试补 P0 变更说明与 Postman collection |

---

## 1. 总览

| 模块 | 路径前缀 | Controller 数 | 本文档 | 既有 Markdown | Postman |
|------|----------|---------------|--------|---------------|---------|
| 用户 / 租户 | `/user/*`、`/internal/keycloak` | 10 | [user.md](modules/user.md) | 无 | ❌ |
| 项目管理 | `/pm/*` | 14 | [pm.md](modules/pm.md) | `docs/pm/pm-api.md`（v1.4） | ✅ `docs/pm/pm-api.postman_collection.json`（P0 变更） |
| 流水线 | `/pipeline/*`、`/dependency-*`、`/argo/*` | 11 | [pipeline.md](modules/pipeline.md) | `docs/pipeline/pipeline-core-api.md`（v0.4） | ✅ `docs/pipeline/pipeline-core-api.postman_collection.json` |
| 容器平台 | `/container/*` | 20 | [container.md](modules/container.md) | `docs/container-platform/container-api.md`（v0.1） | ✅ `docs/container-platform/container-api.postman_collection.json` |
| API 测试 | `/apitest/*` | 10 | [api-test.md](modules/api-test.md) | `docs/backend/api_spec.md`（v0.2） | ✅ `docs/backend/api_spec.postman_collection.json` |
| 文档生成 | `/api/docgen/*` | 1 | [docgen.md](modules/docgen.md) | `docs/docgen/` | ❌ |
| 文件解析 | `/api/file-parser/*` | 1 | [file-parser.md](modules/file-parser.md) | `docs/file-parser/` | ❌ |
| 图片处理 | `/api/image/*` | 1 | [image.md](modules/image.md) | `docs/image/` | ❌ |
| 健康检查 | `/health/*` | 1 | [api-overview.md](api-overview.md) | `README.md` | ❌ |

合计 67 个 Controller（含 1 个健康检查）。流水线、PM、容器、API 测试已提供 Postman collection（覆盖 P0 变更接口）；用户/租户、Docgen、File Parser、Image 仍待补。

## 2. 覆盖说明

| 状态 | 含义 |
|------|------|
| 有 | 有完整请求/响应/错误示例 |
| 部分 | 有路径与部分字段，缺失败示例 |
| 骨架 | 本文档仅列方法、路径、用途；参数与报文待补 |
| 无 | 没有正式接口文档 |

| 模块 | 既有文档覆盖 | 本次骨架 | 待补 |
|------|--------------|----------|------|
| PM | `pm-api.md` 覆盖全部接口，但版本与字段可能滞后 | 全量路径 | 对照代码校验字段；补失败示例与 Postman |
| 流水线 | `pipeline-core-api.md` v0.4 覆盖主流程、WS、参数、产物、依赖与 Argo | 全量路径+报文+示例 | 已完成 Markdown + Postman |
| 容器 | 设计文档零散提及 | 全量路径 | 全部请求/响应/错误；Postman |
| API 测试 | `api_spec.md` 覆盖 api-test 全模块 | 全量路径 | 与代码对齐；补 Postman |
| 用户 | 仅 README 简述 | 全量路径 | 全部请求/响应/错误；Postman |
| Docgen / File Parser / Image | 设计文档提及 | 全量路径 | 全部请求/响应/错误；Postman |

## 3. Postman 补齐计划

按 AGENTS.md「接口变更」要求，每个模块需同时维护：

- `docs/api/<module>/<module>-api.md`
- `docs/api/<module>/<module>-api.postman_collection.json`

建议顺序（按使用频率与风险）：

| 批次 | 模块 | 理由 |
|------|------|------|
| 1 | API 测试、PM | 已有 Markdown 基础，改动频繁 |
| 2 | 流水线 | 核心交付链路，运行/产物/回传接口需回归 |
| 3 | 用户 / 租户 | 认证与权限是其他模块前置 |
| 4 | 容器 | 接口多、K8s 依赖强，需环境变量区分集群 |
| 5 | Docgen / File Parser / Image | 接口少，可快速完成 |

Postman 变量约定：`{{baseUrl}}`、`{{token}}`、`{{tenantId}}`、`{{projectId}}`、`{{clusterId}}` 等，不写死环境地址或密钥。

## 4. 一致性校验

```bash
# Controller 清单
find backend -path '*/src/main/java/*' -name '*Controller.java' | grep -v /target/ | wc -l

# 路径清单
grep -rn '@RequestMapping' backend --include='*.java' | grep -v /target/

# 本文档路径抽查：随机取 5 条，确认在 Controller 中存在
```
