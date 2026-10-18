# HFWAS DevOps 产品文档总索引

> 日期：2026-10-18
> 版本：v0.4
> 状态：首批（骨架 + 核心内容）
> 适用范围：HFWAS DevOps 全部 8 个已上线子系统
> 数据来源：`README.md`、`frontend/src/router.tsx`、`frontend/src/shared/console/products.ts`、`backend/*/controller`、`backend/*/src/main/resources/db/*.sql`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：建立产品文档体系、总索引、文档状态与缺口看板 |
| v0.2 | 2026-10-18 | 更新缺口看板：流水线接口文档与 Postman 已完成 |
| v0.3 | 2026-10-18 | 新增数据库文档规范入口 `03-database/database-documentation-standard.md` |
| v0.4 | 2026-10-18 | 新增前端原型先行规范入口 `docs/frontend/prototype-first-standard.md` |

---

## 1. 文档体系

产品文档按“读者使用路径”组织，而不是按代码模块组织：

```text
产品理解 → 功能规格 → 原型 → 数据库 → 接口 → 测试 → 架构/运维 → 发布
```

| 层级 | 目录 | 回答的问题 | 主要读者 |
|------|------|-----------|----------|
| 总览 | [01-overview/](01-overview/) | 产品是什么、给谁用、边界在哪 | 产品、研发、售前 |
| 功能 | [02-features/](02-features/) | 每个子系统有哪些功能、规则与异常 | 产品、研发、测试 |
| 数据库 | [03-database/](03-database/) | 数据如何建模、表与字段含义 | 后端、DBA、测试 |
| 接口 | [04-api/](04-api/) | 系统对外提供哪些 HTTP 接口 | 前后端、集成方、测试 |
| 测试 | [05-testing/](05-testing/) | 如何验证功能正确性 | 测试、研发 |
| 原型 | [06-prototype/](06-prototype/) | 界面长什么样、如何交互 | 产品、设计、前端 |
| 架构与运维 | [07-operations/](07-operations/) | 如何部署、集成、发布与排障 | 运维、SRE、研发 |

## 2. 子系统导航

| 子系统 | 产品入口 | 功能文档 | 数据库文档 | 接口文档 | 测试用例 |
|--------|----------|----------|-----------|----------|----------|
| 项目管理（PM） | `/pm/projects` | [pm.md](02-features/modules/pm.md) | [pm.md](03-database/tables/pm.md) | [pm.md](04-api/modules/pm.md) | [pm.md](05-testing/cases/pm.md) |
| 用户 / 租户中心 | `/user/accounts` | [user-tenant.md](02-features/modules/user-tenant.md) | [user.md](03-database/tables/user.md) | [user.md](04-api/modules/user.md) | [user-tenant.md](05-testing/cases/user-tenant.md) |
| 流水线 / CI | `/pipeline/pipelines` | [pipeline.md](02-features/modules/pipeline.md) | [pipeline.md](03-database/tables/pipeline.md) | [pipeline.md](04-api/modules/pipeline.md) | [pipeline.md](05-testing/cases/pipeline.md) |
| 容器平台 | `/container/clusters` | [container.md](02-features/modules/container.md) | [container.md](03-database/tables/container.md) · [helm.md](03-database/tables/helm.md) | [container.md](04-api/modules/container.md) | [container.md](05-testing/cases/container.md) |
| API 测试平台 | `/api-test` | [api-test.md](02-features/modules/api-test.md) | [api-test.md](03-database/tables/api-test.md) | [api-test.md](04-api/modules/api-test.md) | [api-test.md](05-testing/cases/api-test.md) |
| 文档生成（Docgen） | `/docgen` | [docgen.md](02-features/modules/docgen.md) | 无独立表 | [docgen.md](04-api/modules/docgen.md) | [docgen.md](05-testing/cases/docgen.md) |
| 文件解析（File Parser） | `/file-parser` | [file-parser.md](02-features/modules/file-parser.md) | 无独立表 | [file-parser.md](04-api/modules/file-parser.md) | [file-parser.md](05-testing/cases/file-parser.md) |
| 图片处理 | `/image` | [image.md](02-features/modules/image.md) | [image.md](03-database/tables/image.md) | [image.md](04-api/modules/image.md) | [image.md](05-testing/cases/image.md) |

## 3. 跨模块文档

| 文档 | 说明 |
|------|------|
| [product-overview.md](01-overview/product-overview.md) | 产品定位、目标用户、核心链路、边界与非目标 |
| [glossary-domain-model.md](01-overview/glossary-domain-model.md) | 术语表与核心领域模型 |
| [roles-permissions.md](01-overview/roles-permissions.md) | 平台角色、租户角色、接口鉴权规则 |
| [feature-catalog.md](02-features/feature-catalog.md) | 8 个子系统功能清单与状态 |
| [feature-spec-template.md](02-features/feature-spec-template.md) | 新增功能规格模板 |
| [database-documentation-standard.md](03-database/database-documentation-standard.md) | 数据库文档规范：四节结构、10 列数据字典、ER/枚举/JSON/迁移要求 |
| [data-dictionary.md](03-database/data-dictionary.md) | 全库 56 张表总览、初始化机制与公共约定 |
| [api-overview.md](04-api/api-overview.md) | 统一响应、鉴权、租户、错误码与分页约定 |
| [api-module-matrix.md](04-api/api-module-matrix.md) | 模块 × 前缀 × Controller × 文档/Postman 覆盖矩阵 |
| [test-spec.md](05-testing/test-spec.md) | 用例编号、优先级、环境、准入准出与自动化映射 |
| [prototype-index.md](06-prototype/prototype-index.md) | 现有原型入口与待建清单 |
| [prototype-first-standard.md](../frontend/prototype-first-standard.md) | 前端原型先行规范：原型规格、可点击原型、状态矩阵、评审冻结与实现对齐 |
| [architecture.md](07-operations/architecture.md) | 系统架构、模块划分、部署拓扑与数据流 |
| [deployment-ops.md](07-operations/deployment-ops.md) | 本地/Helm/集群部署与运维索引 |
| [third-party-integrations.md](07-operations/third-party-integrations.md) | Keycloak、K8s、Tekton、Harbor 等集成清单 |
| [non-functional.md](07-operations/non-functional.md) | 性能、可用性、安全、可观测性现状 |
| [release-notes.md](07-operations/release-notes.md) | 版本发布记录与发布检查单 |
| [user-manual.md](07-operations/user-manual.md) | 面向最终用户的操作手册（骨架） |

## 4. 文档状态图例

| 状态 | 含义 |
|------|------|
| 已完成 | 内容与当前代码一致，可直接作为交付文档 |
| 骨架 | 结构、清单与关键结论已具备，明细待按模块补全 |
| 待建 | 仅有入口或占位，内容尚未编写 |
| 历史归档 | 描述的是已废弃技术栈或旧实现，仅作背景参考 |

当前首批文档状态：

| 文档组 | 状态 | 说明 |
|--------|------|------|
| 总览（01） | 已完成 | 定位、术语、角色矩阵已基于代码与 README 固化 |
| 功能（02） | 骨架 | 8 个模块均有能力清单、页面路由、规则与异常；逐功能验收标准待细化 |
| 数据库（03） | 已完成（表级） | 56 张表、关键字段、索引、写入方齐全；全字段枚举说明后续补充 |
| 接口（04） | 骨架 | 全部 67 个 Controller 的方法与路径已收录；请求/响应报文与 Postman 待按模块补齐 |
| 测试（05） | 骨架 | 8 个模块各有 10–15 条核心用例；全量用例与自动化映射后续迭代 |
| 原型（06） | 部分完成 | pipeline 已有高保真原型；其余模块标“待建” |
| 架构运维（07） | 已完成（索引级） | 架构、集成、部署入口已整理；SLO/容量等指标标“待采集” |

## 5. 缺口看板（还缺少哪些文档）

### 5.1 本轮已创建

- 产品总览、术语与领域模型、角色权限矩阵
- 功能清单、功能规格模板、8 个模块功能规格
- 全库数据字典（56 张表）+ 7 份模块表字典
- 接口总览、接口覆盖矩阵、8 个模块接口骨架
- 测试规范、8 个模块核心测试用例
- 原型索引、系统架构、部署运维索引、第三方集成、非功能、发布说明、用户手册骨架

### 5.2 引用现有文档、不重复编写

| 主题 | 现有文档 |
|------|----------|
| 错误码规范与清单 | [../backend/error-code-design.md](../backend/error-code-design.md) |
| PM 详细设计 | [../pm/pm-design.md](../pm/pm-design.md)、[../pm/pm-api.md](../pm/pm-api.md) |
| 流水线详细设计与 API | [../pipeline/](../pipeline/)、[../pipeline/pipeline-core-api.md](../pipeline/pipeline-core-api.md) |
| 容器平台设计与实施 | [../container-platform/](../container-platform/) |
| API 测试平台设计 | [../api-test/](../api-test/)、[../backend/api_spec.md](../backend/api_spec.md) |
| 前端协作规范 | [../frontend/ai-collaboration-frontend.md](../frontend/ai-collaboration-frontend.md) |
| 本地与生产部署 | [../devops/](../devops/)、[../container-platform/harbor-deployment.md](../container-platform/harbor-deployment.md) |
| 后端模块架构 | [../backend/module_arch.md](../backend/module_arch.md) |

### 5.3 后续迭代（当前缺口）

| 缺口 | 优先级 | 说明 |
|------|--------|------|
| 全量接口明细 | P0 | 流水线已完成；其余 7 个模块仍需补充请求参数、正常/失败响应示例 |
| Postman collection | P0 | 流水线已提供；用户/PM/容器/API 测试/Docgen/File Parser/Image 仍待补 |
| 全量测试用例 | P1 | 当前为每模块核心场景；需覆盖边界值、异常、权限、并发与数据隔离 |
| 其余模块高保真原型 | P1 | 仅 pipeline 有原型；pm/api-test/container 需补 |
| 用户操作手册 | P1 | 需基于真实界面走查后补截图与逐步操作 |
| 非功能基线 | P2 | 无 SLO、容量、性能测试基线，需采集后回填 |
| 权限矩阵细化 | P2 | 当前权限主要由“已认证 + 平台 admin”控制，模块内细粒度权限未定义 |
| 历史文档归档标记 | P2 | `docs/pm/`、`docs/api-test/`、`docs/container-platform/` 中仍描述 Vue 3 + Naive UI，需标注“历史归档”或重写 |

## 6. 维护规则

1. 所有文档遵守仓库规范：标题 → 日期/版本/状态/来源 → **变更记录** → 正文；每次实质性修改升次版本并追加一行。
2. 功能变更顺序：先改 [feature-catalog.md](02-features/feature-catalog.md) 与对应功能规格 → 再改接口/数据库文档 → 再改测试用例 → 最后改代码。
3. 新增接口必须同步更新 `04-api/` 与 Postman collection（见 `AGENTS.md`「接口变更」）。
4. 新增数据表必须同步更新 `03-database/`，并说明 `embedded` 与 `external` 两种初始化模式下的落点。
5. 测试用例是长期资产：需求变更时先改用例，再改实现。
6. 无法从仓库确认的信息统一写“待确认/待采集”，禁止编造角色、SLA、截图或接口字段。
