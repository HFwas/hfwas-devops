# 测试规范

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成
> 数据来源：`docs/general/test_strategy.md`、各模块 `src/test`、前端 `*.test.tsx`、`docs/superpowers/features/20261017-pm-frontend-spec-cleanup/test-cases.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：用例编号、优先级、环境、准入准出、自动化映射与执行方式 |

---

## 1. 测试分层

| 层级 | 范围 | 工具 / 位置 |
|------|------|-------------|
| 后端单元测试 | Service、工具类、转换器 | JUnit 5 + AssertJ + Mockito，`backend/*/src/test/java` |
| 后端集成测试 | Controller + Mapper + SQLite | Spring Boot Test + MockMvc，`backend/*/src/test/java` |
| 前端单元/组件测试 | 页面逻辑、工具函数、组件 | Vitest，就近 `*.test.ts(x)` |
| 接口测试 | HTTP 接口契约与业务流 | Postman（待建设）/ 手工 |
| 端到端测试 | 跨模块业务链路 | 手工（本地 docker-compose 或 k3s） |
| 非功能测试 | 性能、容量、安全 | 待建设 |

## 2. 用例编号

格式：`<MODULE>-<NNN>`，模块代码：

| 代码 | 模块 |
|------|------|
| `PM` | 项目管理 |
| `USER` | 用户 / 租户 |
| `PIPE` | 流水线 |
| `CON` | 容器平台 |
| `API` | API 测试 |
| `DOC` | 文档生成 |
| `FP` | 文件解析 |
| `IMG` | 图片处理 |

编号一旦分配不复用；废弃用例标记“已废弃”而不是删除。

## 3. 优先级

| 级别 | 定义 | 要求 |
|------|------|------|
| P0 | 核心主流程、数据安全、权限隔离 | 每次发布必测，必须自动化 |
| P1 | 主要功能、常见异常 | 每次发布必测，优先自动化 |
| P2 | 边界值、次要功能 | 版本回归覆盖 |
| P3 | 体验、提示文案、罕见场景 | 按需执行 |

## 4. 测试环境

| 环境 | 说明 |
|------|------|
| 本地 docker-compose | backend 8089、frontend 5173、Kong、Keycloak |
| macOS | k3s 运行在 Docker 容器内，操作方式见 `AGENTS.md`「本地部署」 |
| Windows | Docker Desktop Kubernetes，context `docker-desktop` |
| 测试数据库 | SQLite；后端集成测试使用内存库或临时文件 |
| 外部依赖 | Keycloak、Tekton、Harbor、Prometheus、Dependency-Track（按用例需要） |

## 5. 测试数据

| 数据 | 说明 |
|------|------|
| 租户 | 默认租户 + 至少一个独立租户（用于隔离用例） |
| 用户 | 平台 `admin`、普通 `user`、租户 `tenant_admin`、租户 `member` |
| 项目 | 至少两个项目（用于项目隔离） |
| 流水线 | 一条最小可运行流水线 + 一条含多阶段/多参数的流水线 |
| 集群 | 本地 k3s 或 docker-desktop |
| 文件 | 各格式样例（可先用 Docgen 生成） |

## 6. 准入与准出

**准入**：

1. 代码编译通过，数据库 schema 可初始化；
2. 后端与前端服务可启动，Keycloak 可登录；
3. 被测功能有对应的功能规格与接口文档；
4. 测试数据准备完成。

**准出**：

1. P0/P1 用例全部通过；
2. P2 用例通过率 ≥ 95%，未通过项有明确缺陷单；
3. 无阻塞级/严重级未修复缺陷；
4. 回归测试覆盖本次变更影响范围；
5. 测试报告记录环境、版本、用例执行结果与遗留风险。

## 7. 自动化映射

| 模块 | 后端测试位置 | 前端测试位置 |
|------|--------------|--------------|
| PM | `backend/server/src/test/java/.../pm/` | `frontend/src/modules/pm/**/*.test.ts(x)` |
| 用户 / 租户 | `backend/user-core/src/test/java/` | `frontend/src/modules/user/**/*.test.ts(x)` |
| 流水线 | `backend/pipeline-core/src/test/java/` | `frontend/src/modules/pipeline/**/*.test.ts(x)` |
| 容器 | `backend/container-core/src/test/java/` | `frontend/src/modules/container/**/*.test.ts(x)` |
| API 测试 | `backend/api-test-core/src/test/java/` | `frontend/src/modules/api-test/**/*.test.ts(x)` |
| Docgen | `backend/server/src/test/java/.../docgen/` | `frontend/src/modules/docgen/**/*.test.ts(x)` |
| 文件解析 | `backend/file-parser/src/test/java/` | `frontend/src/modules/file-parser/**/*.test.ts(x)` |
| 图片 | `backend/image-core/src/test/java/` | `frontend/src/modules/image/**/*.test.ts(x)` |

> 具体路径以仓库实际存在的测试文件为准；用例文档中标注“待补自动化”的条目需在实现后回填 `文件::测试名`。

## 8. 执行方式

```bash
# 后端全量
mvn -q test

# 单模块
mvn -q -pl backend/pipeline-core test

# 前端
cd frontend && npm test

# 手工端到端
docker compose up -d
# 或按 AGENTS.md 启动 k3s / docker-desktop
```

## 9. 缺陷分级

| 级别 | 定义 | 示例 |
|------|------|------|
| 阻塞 | 主流程不可用、数据损坏、越权 | 登录失败、跨租户读到数据 |
| 严重 | 核心功能错误、明显数据错误 | 运行状态不更新、金额/统计错误 |
| 一般 | 次要功能错误、异常处理不当 | 提示错误、分页错乱 |
| 轻微 | 文案、样式、体验 | 对齐、错别字 |

## 10. 用例维护

1. 需求变更时**先改用例文档，再改代码**；
2. 每条用例必须写明前置条件、步骤、输入与预期结果；
3. 用例与自动化测试双向可追溯：用例标注测试位置，测试注释引用用例编号；
4. 发布前按 `05-testing/cases/` 逐模块执行并记录结果。
