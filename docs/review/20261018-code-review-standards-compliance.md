# 存量代码规范符合性审查报告

> 日期：2026-10-18
> 版本：v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：按 `AGENTS.md` 及关联规范，六维度并行审查 `dev` 分支存量代码；去重后 5 个 P0、29 个 P1、53 个 P2 |
| v0.2 | 2026-10-18 | 追加 P0 实施后复核结论；P0-0/2/3/4/5 已完成，P0-1 按用户决定未实施 |

---

## 1. 审查范围与方法

### 1.1 基线

| 项 | 值 |
|----|----|
| 分支 / HEAD | `dev` / `d215a9d` |
| 工作区 | 干净，未做任何修改 |
| 代码规模 | 863 个 Java 文件、288 个前端 TS/TSX 文件、67 个 Controller、167 份 docs Markdown |
| 审查方式 | 只读静态审查 + grep/find 统计；数据库维度用临时 SQLite 分别构建 external/embedded 库实测；未启动服务、未跑全量测试 |

### 1.2 依据规范

- `AGENTS.md`：接口变更、数据库变更、数据库文档、前端、原型先行、新增功能、提交前测试
- `docs/backend/error-code-design.md`
- `docs/product/04-api/api-overview.md`、`api-module-matrix.md`、`modules/*.md`
- `docs/product/03-database/database-documentation-standard.md`、`data-dictionary.md`
- `docs/frontend/ai-collaboration-frontend.md`、`prototype-first-standard.md`、`kite-frontend-style-guide.md`、`shared-ui-patterns.md`
- `docs/superpowers/features/README.md`

### 1.3 严重度定义

| 级别 | 含义 |
|------|------|
| P0 | 可直接导致未授权控制、跨租户数据泄漏、数据丢失，或阻塞发布 |
| P1 | 高风险越权/可靠性/门禁失效，需在下一迭代内修复 |
| P2 | 规范偏差、可维护性、文档与配置问题，按计划偿还 |

### 1.4 可信度说明

- **全部 5 个 P0 已由主会话独立复核代码路径**，不是仅凭 agent 推断。
- P1 中凭证加密、登录日志删除、`migrate.py` 部分执行、`sys_login_log`、Helm 迁移执行器等关键项已复核。
- 其余 P1/P2 为静态审查结论，均附 `file:line` 证据；未做真实 HTTP 复现的项已单独标注。
- 未运行 `mvn test`、`npm test`、`tsc --noEmit`，测试通过数未独立复跑。

---

## 2. 总览

| 维度 | P0 | P1 | P2 | 核心结论 |
|------|----|----|----|----------|
| 安全与仓库边界 | 1 | 5 | 8 | 交付运维平台无认证；容器 WS 跨租户；默认加密密钥与明文 kubeconfig |
| 后端接口契约 | 2 | 7 | 5 | PM 与 API 测试模块存在按 ID 越权；错误码/HTTP 状态大量偏离规范 |
| 数据库 | 1 | 5 | 8 | 破坏性迁移可丢数据；双模式结构/种子不一致；7 份库文档不合规 |
| 前端 | 0 | 3 | 10 | 技术栈合规；图表硬编码色、危险删除绕过共享组件、原生控件泛滥 |
| 功能文档与原型 | 0 | 2 | 13 | PM 功能缺原型且未记豁免；流水线原型缺冻结记录；docs 历史存量缺版本头 |
| 测试与 CI | 0（原 1 降级） | 6 | 9 | CI 真跑测试，但 `pm-core` 0 测试、容器仅测 Helm、无覆盖率门禁 |
| **合计（去重）** | **5** | **29** | **53** | 原始 88 条，合并 1 组重复 |

**一句话结论：** 技术栈与 CI 门禁本身基本合规，真正的问题是**租户隔离在多个模块失效、交付运维平台完全无认证、数据库迁移存在破坏性重跑路径**；这三类风险优先于任何样式或文档问题。

---

## 3. P0（发布阻断，建议立即处理）

### P0-1 交付运维平台后端无任何认证，NodePort 暴露后可未授权控制集群

**位置**
- `delivery-platform/backend/cmd/server/main.go:40-59`
- `delivery-platform/backend/internal/api/handler.go:30-67`
- `delivery-platform/deploy/k8s/30-frontend.yaml:62`
- `delivery-platform/deploy/k8s/10-rbac.yaml:28-55`

**证据（已复核）**
- 路由只挂 `middleware.Logger`、`middleware.Recoverer`、CORS，没有认证/授权中间件。
- `/api/delivery/clusters` 直接接收 kubeconfig；`/products/{id}/deploy|rollback|uninstall`、节点/Pod/PVC 查询均无鉴权。
- 前端 Service 为 `NodePort 30881`，nginx 把 `/api/` 反代到后端；配套 ClusterRole 对 nodes/pods/services 有 `create`、对交付 CRD 有 `delete`。

**影响**：任何能访问 30881 的人可导入集群凭据、部署/卸载产品、读写集群资源，等同未认证的 Kubernetes 控制面。

**修复方向**
1. 接入 Keycloak/JWT，或至少共享密钥 + 来源限制。
2. 默认改 ClusterIP + Ingress；NodePort 仅本地 dev 使用。
3. 写操作补租户/角色校验，RBAC 收敛到最小权限。

---

### P0-2 PM 事项/评论/看板/导入导出未做租户或项目归属校验

**位置**
- `backend/pm-core/src/main/java/com/hfwas/devops/pm/workitem/service/WorkItemService.java:58-62,99-107`
- `backend/pm-core/src/main/java/com/hfwas/devops/pm/query/engine/QueryEngine.java:27-44`
- `backend/pm-core/src/main/java/com/hfwas/devops/pm/workitem/io/WorkItemImportExportService.java:413-417`

**证据（已复核）**
- `getById` 仅 `selectById(id)`；`transition` 只按 id 取旧值；删除路径同样无归属校验。
- `QueryEngine.execute` 仅在 `projectId != null` 时加 `project_id` 条件，从不校验项目属于当前租户。
- 主键 `IdType.AUTO`，ID 可枚举。

**影响**：任意登录用户可按自增 ID 跨租户读取、修改、删除事项；导入可写入任意项目。

**修复方向**：所有按 id 的读写入口统一校验 `project.tenantId == currentTenantId`；SQL 条件强制带 tenant/project；补跨租户越权测试。

---

### P0-3 API 测试模块信任客户端 `userId`，且不校验项目归属

**位置**
- `backend/api-test-core/src/main/java/com/hfwas/devops/apitest/apidefine/controller/ApiDefinitionController.java:53-91`
- `backend/api-test-core/src/main/java/com/hfwas/devops/apitest/apidefine/service/ApiDefinitionService.java:188-205`
- `backend/api-test-core/src/main/java/com/hfwas/devops/apitest/collection/controller/CollectionController.java:54-57`

**证据（已复核）**
- `getDetail/update/delete/publish/deprecate/revert-draft` 仅 `getById(id)` 后直接操作，无项目/租户校验。
- `create/update/publish/deprecate/revert-draft` 用 `@RequestParam Long userId` 决定操作人，调用方可任意伪造。
- 全模块共 16 处客户端传入 `userId`。

**影响**：可伪造操作人，并跨项目/租户读写接口定义、集合与环境。

**修复方向**：`userId` 从 SecurityContext 获取并删除公开参数；所有 `projectId` 入口复用 PM 项目访问校验；补越权测试。

---

### P0-4 容器平台 WebSocket 租户隔离失效，可跨租户读日志/进容器

**位置**
- `backend/container-core/src/main/java/com/hfwas/devops/container/ws/LogTailHandler.java:81`
- `backend/container-core/src/main/java/com/hfwas/devops/container/ws/ResourceWatchHandler.java:73`
- `backend/container-core/src/main/java/com/hfwas/devops/container/ws/PodShellWebSocketHandler.java:79-80`
- `backend/container-core/src/main/java/com/hfwas/devops/container/service/cluster/ClusterService.java:84-100`

**证据（已复核）**
- 日志与资源 Watch 只校验 `userId != null`，随后调用 `getByIdInternal(clusterId)`，完全跳过租户校验。
- Pod Shell 在 WebSocket 线程调用 `SecurityHelper.currentTenantId()`，读取 `SecurityContextHolder`；握手拦截器只把 `tenantId` 写入 session attributes，没有写入 SecurityContext，因此通常得到 `null`。
- `getById(clusterId, null)` 的判空逻辑会跳过租户比较。

**影响**：任意持有有效 JWT 的用户可访问任意租户集群的 Pod 日志、资源事件，甚至进入容器执行命令。

**修复方向**：统一从握手 attributes 恢复 `tenantId` 并强制非空；删除或收紧 `getByIdInternal` 的外部可达调用；租户上下文缺失时 fail closed。

---

### P0-5 external 迁移含无条件 DROP，重跑会清空依赖组件数据

**位置**
- `deploy/charts/backend/files/db/05-pipeline-schema.sql:320`
- `deploy/charts/backend/files/db/migrate.py:42-72`
- `deploy/charts/backend/files/db/migrate.sh:32,48,76`
- `deploy/charts/backend/templates/deployment.yaml:49-51`

**证据（已复核）**
- `05-pipeline-schema.sql` 第 320 行无条件执行 `DROP TABLE IF EXISTS dependency_component;`。
- `migrate.py` 按文件 checksum 决定是否重跑；文件被修改后 checksum 变化会整文件重跑。
- `migrate.sh` 写 `_schema_migrations`，`migrate.py` 写 `schema_migrations`，两个执行器互相看不到对方记录。
- Helm initContainer 实际执行的是 `python3 /sql/migrate.py`，与 `AGENTS.md` 所述 `migrate.sh` 不一致。

**影响**：升级到任一修改过的 05 版本、或切换迁移执行器时，会删除全部 `dependency_component` 行。

**修复方向**：删除 05 中的 DROP；破坏性结构调整改为新增迁移并保留数据；统一迁移记录表；修正 `AGENTS.md` 的执行器说明。

---

## 4. P1（下一迭代内处理）

### 4.1 安全与配置

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-S1 | 容器 HTTP 路径在 `tenantId` 为空时跳过租户过滤 | `TenantContextService.java:36-59`、`TenantContextFilter.java:47-50`、`ClusterService.java:70-78`、`RegistryService.java:76-100` | container 模块文档“按 tenant_id 隔离” | 租户缺失 fail closed，抛 `TENANT_CONTEXT_MISSING`；移除 nullable 条件 |
| P1-S2 | 流水线产物接口无归属校验，上传接口匿名开放 | `SecurityConfig.java:41`、`PipelineRunArtifactController.java:34-55`、`PipelineRunArtifactService.java:48-53,114-133` | api-overview §6；PIPE-916/917 | 上传改用集群内 token；list/download 校验 run 归属与 `artifactId.runId == runId` |
| P1-S3 | 凭证加密使用仓库内置默认密钥，部署未注入 | `KubeconfigCipher.java:26`、`RegistryCredentialCipher.java:26`、`CredentialCipher.java:22`、`application.yml:81` | 密钥不得随代码分发 | 移除默认值，缺失即启动失败；Compose/Helm 用 Secret 注入并轮换 |
| P1-S4 | 交付运维平台 kubeconfig 实际明文落库 | `delivery-platform/backend/internal/api/handler.go:1008-1014`、`migrations/001_init.sql:8` | 迁移注释与设计声明 AES-256-GCM | 实现真实 AES-256-GCM + 独立密钥；清理既有明文 |
| P1-S5 | Compose 把宿主机 `~/.kube` 整目录挂入后端 | `docker-compose.yml:126-134` | 最小暴露面 | 只挂载专用最小权限 kubeconfig |
| P1-S6 | Kong file-log 可能记录 `Authorization` | `kong/kong.yml:151-155` | 日志不得记录凭据 | **推测**，需查 `logs/kong/request.log` 确认；关闭插件或做脱敏 |

### 4.2 后端接口契约

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-B1 | `ApiTestException` 固定 `code=400`，42 处业务错误返回 HTTP 200 + 400 | `ApiTestException.java:15-18`、`ExceptionAdvice.java:31-36` | error-code §3/§4.5/§5.4 | 改用 `BizException + ResultCode`；同步文档与测试 |
| P1-B2 | 权限类 `BizException` 仍返回 HTTP 200，未映射 401/403 | `ExceptionAdvice.java:24-29`、`ClusterService.java:87`、`RegistryService.java:98` | error-code §4.3/§4.4/§4.5 | 在 Advice 中复用 `ApiErrorWriter.resolveHttpStatus` |
| P1-B3 | 缺参/类型不匹配/约束校验异常落入 10001 | `ExceptionAdvice.java:68-77` | error-code §4.2 | 补 `MissingServletRequestParameterException`、`ConstraintViolationException`、`MethodArgumentTypeMismatchException` |
| P1-B4 | 资源不存在返回 `code=0`（PM 项目/事项、PodExec） | `ProjectService.java:84-88`、`WorkItemService.java:58-61`、`PodExecController.java:62-71,201-208` | pm-api §3.3；error-code §8 | 抛 NOT_FOUND；删除“空 VO 成功”分支 |
| P1-B5 | FileParser/Docgen 使用 400/500 magic code | `FileParserController.java:37,46`、`DocgenController.java:105-107` | error-code §3/§5.4 | 改用 `ResultCode`；修正模块文档与 Postman |
| P1-B6 | 7/8 模块无 Postman collection；流水线数量/覆盖不符 | `docs/product/04-api/api-module-matrix.md:21-29`、`docs/pipeline/pipeline-core-api.postman_collection.json` | AGENTS「接口变更」 | 按矩阵补齐；每接口正常+失败 example |
| P1-B7 | api-test 断言/提取实体映射了不存在的列 | `ApiDefinitionAssertionEntity.java:44,47`、`ApiDefinitionExtractEntity.java:41,44`、`sql/api-test-schema.sql:102-130` | database standard §3.3/§6.1 | 新增迁移补 `description`/`sort_order`，或临时 `@TableField(exist=false)` |

### 4.3 数据库

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-D1 | embedded 每次启动 DROP 重建 `sys_login_log`，登录审计必丢 | `UserSchemaMigration.java:34,139-141` | 幂等且不可破坏数据 | 去掉无条件 DROP，改列检测/ALTER |
| P1-D2 | `migrate.py` 遇 duplicate column 中断整文件，却仍写 checksum | `migrate.py:53-72` | AGENTS「SQL 应幂等」 | 逐语句执行并单独处理重复列；失败不写 checksum |
| P1-D3 | `sys_user` 双模式不一致：external 缺 `tenant_id`、默认值/索引不同 | `01-user-schema.sql:20-35`、`UserSchemaMigration.java:67-82,132,215,219` | AGENTS「两种模式建表结果一致」 | 以 embedded 为权威补齐 external |
| P1-D4 | pipeline 种子双模式不一致：external 多 KUBECTL、6 个 DT 参数、7 个命令模板 | `backend/server/src/main/resources/db/pipeline-schema.sql:217`、`deploy/.../05-pipeline-schema.sql:182-187,226,287,503-512` | 双模式一致 | 补 classpath 种子或共用同一份种子文件 |
| P1-D5 | 7 份模块数据库文档全部不符合四节结构/10 列字典/ER 图要求 | `docs/product/03-database/tables/*.md`、`data-dictionary.md:5` | database-documentation-standard §3/§6/§9 | 按第 13 节模板重构，优先 pipeline → pm → user |

### 4.4 测试与 CI

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-T1 | `pm-core` 144 个主类、0 测试类，pom 无测试依赖 | `backend/pm-core/pom.xml:22`、`backend/server/pom.xml:30` | AGENTS 提交前测试；test-spec P0 必须自动化 | 引入 starter-test + surefire；先覆盖状态流转/事项/条件求值 |
| P1-T2 | `container-core` 62 个测试方法全部集中在 Helm | `container-core/src/test/.../service/helm/` | 同上 | 补 Cluster/Resource/PodFile/Registry/Prometheus 单测 |
| P1-T3 | `server`/`user-core`/`user-api` 测试严重不足 | `server/src/test/.../ws/`、`user-core/src/test/`、`user-api/` | 同上 | 优先租户隔离、登录/鉴权、401/403 |
| P1-T4 | `test-spec.md` 把 PM/Docgen 测试指向不存在的目录 | `docs/product/05-testing/test-spec.md:95,100` | AGENTS 新增功能第 5 步 | 改为真实路径，未覆盖项显式标注 |
| P1-T5 | OCR 集成测试被属性开关永久跳过，且无 failsafe | `OcrIntegrationTest.java:39-40`、`maven.yml:29`、`pr-backend.yml:36` | 提交前测试 | 增加 integration job 或引入 failsafe |
| P1-T6 | 前端 PM/user/docgen/file-parser 页面无测试 | `frontend/src/modules/{pm,user,docgen,file-parser}/pages/` | 前端 PR 执行 npm test | 按 P0 页面补渲染/空态/失败/权限测试 |

> 原测试维度将“PM 零测试”定为 P0；本报告按其不直接造成数据泄漏降为 P1，但仍列为最高优先级的质量门禁问题。

### 4.5 前端

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-F1 | 容器监控 10 个系列色为 Naive UI 遗留 hex，不读 `--chart-*` | `container/types/monitor.ts:19`、`ResourceMonitors.tsx:59-429`、`MonitorLineChart.tsx:60-77` | kite style §5.5 | 从 CSS 变量读取 `--chart-1..5` |
| P1-F2 | 5 处危险删除仍用 `window.confirm` | `ClusterListPage.tsx:141`、`RegistryListPage.tsx:128`、`RegistryDrillPages.tsx:259`、`WorkloadDetailPages.tsx:62`、`DefinitionDetailPage.tsx:124` | shared-ui-patterns §1 | 替换为 `ConfirmDialog`，文案写明后果 |
| P1-F3 | 11 处原生 select、11 处原生 checkbox/radio 绕过 `components/ui`，且缺 4 个原语 | 见各页面；`src/components/ui/` | ai-collaboration §3.2.5 | `npx shadcn add checkbox switch tabs command`；select 改 `Select` |

### 4.6 功能文档与原型

| ID | 结论 | 位置 | 依据 | 修复方向 |
|----|------|------|------|----------|
| P1-DOC1 | PM 前端整改为跨 3 文件 UI 改动，但无 `prototype/` 且未记豁免 | `docs/superpowers/features/20261017-pm-frontend-spec-cleanup/` | prototype-first §2.1/§2.2 | 补豁免说明，或补 L1 画板 + 冻结记录 |
| P1-DOC2 | 流水线原型被规范引为范例，但缺评审/冻结记录，且连线表引用不存在的画板 | `docs/pipeline/prototype/README.md:1-12,129` | prototype-first §9.3/§13/§14 | 补冻结表；补 `P03_PipelineEditor_New_Desktop` 画板或改连线 |

---

## 5. P2（按计划偿还）

### 5.1 安全与仓库边界（8）

| ID | 结论 | 位置 |
|----|------|------|
| P2-S1 | 所有 WebSocket 端点 `setAllowedOriginPatterns("*")` | `PodExecWebSocketConfig.java:24-26`、`ContainerWsConfig.java:29-39` |
| P2-S2 | Keycloak/Harbor/GitLab/Dependency-Track 使用公开弱默认口令 | `keycloak/hfwas-devops-realm.json:73`、各 chart `values.yaml` |
| P2-S3 | `.gitignore` 未覆盖 `.env*`、`*.pem`、`*.key`、`*kubeconfig*` | `.gitignore:46-64` |
| P2-S4 | Actions 使用可变 tag，部分 workflow 无 `permissions` | `.github/workflows/maven.yml:20-32`、`codeql-analysis.yml:37-69` |
| P2-S5 | 应用镜像/调试镜像使用 `latest` | `deploy/charts/*/values.yaml`、`PodExecWebSocketHandler.java:42` |
| P2-S6 | 前端运行时 root；Desktop values `runAsUser: 0`；OTel initContainer root | `deploy/docker/frontend/Dockerfile:28-38`、`values-desktop.yaml:20-37` |
| P2-S7 | `delivery-platform/` 已入库并接入本仓库 CI/Dependabot/Helm，与“独立产品”意图有偏差；AGENTS 引用的设计文档路径不存在 | `AGENTS.md:67`、`delivery-platform/`、`.github/workflows/pr-frontend.yml:9` |
| P2-S8 | Keycloak 事件回写匿名可达且默认密钥公开 | `SecurityConfig.java:39`、`application.yml:28`、`docker-compose.yml:247` |

### 5.2 后端接口契约（5）

| ID | 结论 | 位置 |
|----|------|------|
| P2-B1 | 容器错误码 30001-30313 未在错误码分段/`ResultCode` 中分配 | `ContainerErrorCode.java:11-42`、`error-code-design.md §3` |
| P2-B2 | 分页字段名/默认值/上限不统一（`pageNum` vs `pageNo`，10/20/200） | `PageRequest.java:11-26`、各模块 DTO、`api-overview.md:103` |
| P2-B3 | PM/user 仍大量抛裸 `IllegalArgumentException`（pm-core 165 处） | `WorkItemService.java:84,102,106`、`NotifyChannelService.java:45` |
| P2-B4 | Keycloak webhook 鉴权失败返回 HTTP 200 + 10001 | `KeycloakEventController.java:30-31`、`ExceptionAdvice.java:85-90` |
| P2-B5 | 总览/模块 API 文档与代码不一致；`pm-api.md` 无变更记录 | `api-overview.md:47,103,122,143`、`docs/pm/pm-api.md:1-5` |

### 5.3 数据库（8）

| ID | 结论 | 位置 |
|----|------|------|
| P2-D1 | 迁移编号 06 直接跳到 10，缺 07-09（历史从未存在） | `deploy/charts/backend/files/db/` |
| P2-D2 | 枚举文档与代码/SQL 不一致（API 状态大小写、image 状态、待确认项） | `data-dictionary.md:146-151` |
| P2-D3 | classpath helm schema 重复 ALTER，每次启动触发 4 条 duplicate 并被 `continueOnError` 吞掉 | `helm-chart-schema.sql:28-45`、`SqliteSchemaInitializer.java:48` |
| P2-D4 | 命名违规：裸 `type` 列、唯一索引用 `idx_`、JSON 字段缺 `_json` 后缀 | `container-schema.sql:26`、`user-schema.sql:98`、`helm-chart-schema.sql:9` |
| P2-D5 | pm schema 重复定义 `sys_user`，与 user schema 漂移 | `pm-schema.sql:224-236` |
| P2-D6 | data-dictionary 漏列 3 个 embedded runner；`schemaVersion` 实际不存在 | `data-dictionary.md:69-84`、`AGENTS.md:342` |
| P2-D7 | pipeline.md 种子/迁移描述过时（KUBECTL、17 vs 18、version 表） | `tables/pipeline.md:181,293` |
| P2-D8 | 生产连接未启用 SQLite 外键，声明的 FK/`ON DELETE` 不生效 | `application.yml:11`、`pipeline-schema.sql:303-322` |

### 5.4 前端（10）

| ID | 结论 | 位置 |
|----|------|------|
| P2-F1 | 原型索引中 PM/用户/容器/API 测试/Docgen/文件解析/图片/工作台均为“无/待建” | `docs/product/06-prototype/prototype-index.md:50-57` |
| P2-F2 | API 测试模块残留 Element Plus 色板且已成死代码（13 个 `color: '#...'`） | `api-test/define/types/definition.ts:150-165`、`debug/types/debug.ts:5-9` |
| P2-F3 | 流水线 `runStatusTagType`/`stackTone`/`jobKindTone` 与空 `CONSOLE_TABS` 为死代码 | `pipeline/status.ts:14-30`、`shared/console/tabs.ts:22-27` |
| P2-F4 | 5 个未使用依赖；Radix 伞包与单独包并存 | `frontend/package.json:14-34` |
| P2-F5 | 3 个表单用 useState，未走 RHF + Zod | `WorkItemDetailPage.tsx:179-198`、`DocgenPage.tsx:42-72`、`HelmUploadPage.tsx:58-63` |
| P2-F6 | `tsconfig.app.json` 残留 `src/**/*.vue` 与 `jsx: preserve`，且未被引用 | `frontend/tsconfig.app.json:13,20` |
| P2-F7 | 3 个业务 UI 文件超过 500 行（最大 1088 行） | `PipelineEditorPage.tsx`、`WorkloadDetailPages.tsx`、`ResourceMonitors.tsx` |
| P2-F8 | 详情字段渲染重复实现，未复用 `InfoGrid`/`InfoPairs` | `WorkloadDetailPages.tsx:152-161`、`HelmReleaseDetailPage.tsx:226-233` |
| P2-F9 | 生产类型 5 处 `any` | `api-test/define/types/definition.ts:35-36,102-103`、`debugHistory.ts:36` |
| P2-F10 | 路由 45 个页面全静态导入，无 `React.lazy` 分包 | `frontend/src/router.tsx:3-64` |

### 5.5 功能文档与原型（13）

| ID | 结论 | 位置 |
|----|------|------|
| P2-DOC1 | PM `test-cases.md` 文首 v0.2，变更记录最新为 v0.3 | 同文件 `:4,:12` |
| P2-DOC2 | 两份 `progress.md` 只有第 0 轮回溯，缺 `HH:mm`，历史轮次未逐条记录 | 两个功能目录 `progress.md:15` |
| P2-DOC3 | 流水线 `test-cases.md` 只有映射表，无前置/步骤/输入/预期 | `20261018-pipeline-full-automation/test-cases.md:31` |
| P2-DOC4 | 16 条自动化映射未到 `文件::测试名` 粒度 | 同文件 `:167` 起 |
| P2-DOC5 | 流水线原型约 40 处 hex + 11 处 rgb，违反 token 约束 | `docs/pipeline/prototype/index.html:108,317-320,864` |
| P2-DOC6 | 原型状态矩阵缺无权限、大数据量、长文本 | `prototype/README.md:54`、`index.html:522-703` |
| P2-DOC7 | 原型缺固定「原型 vX.Y · 非生产环境」标识 | `prototype/index.html:6,440` |
| P2-DOC8 | `prototype-spec.json` 缺 `viewport.scale`、`pages[].hash`、`acceptance` | `pipeline-management-prototype-spec.json:6,68` |
| P2-DOC9 | 连线表引用不存在的 `P03_PipelineEditor_New_Desktop` 画板 | `prototype/README.md:129` |
| P2-DOC10 | 原型 README 缺 hash ↔ 生产路由对应表 | `prototype/README.md:40` |
| P2-DOC11 | 65 份历史 docs 缺日期/版本/变更记录至少一项（均 ≤ 2026-09-12） | 代表：`docs/backend/api_spec.md:1`、`docs/evolution/*` |
| P2-DOC12 | 流水线 `changes.md` 校验结论仍写“初步”，与“独立校验通过”口径不一致 | `changes.md:127` |
| P2-DOC13 | 前端测试数口径差 1（77 通过 + 1 跳过，共 78 条声明） | `changes.md:92` |

### 5.6 测试与 CI（9）

| ID | 结论 | 位置 |
|----|------|------|
| P2-T1 | api-test 约 174 个测试方法在测 getter/setter 与枚举常量 | `CollectionEntityTest.java:66-93`、`EnumTest.java:49-56` |
| P2-T2 | 时序测试用固定 `Thread.sleep(50/80/1500)`，CI 抖动风险 | `ImageWorkLimiterTest.java:43`、`HttpDebugEngineTest.java:155` |
| P2-T3 | 存在 `it.skip` + `expect(true)` 空断言占位（PIPE-910） | `PipelineFlow.test.tsx:219-221` |
| P2-T4 | `delivery-platform` Go 后端 0 测试，CI 空跑通过 | `.github/workflows/pr-go.yml:58-59` |
| P2-T5 | `cn-app-operator` 4 个 Controller 与 status 聚合器无测试 | `cn-app-operator/controllers/`、`pkg/status/aggregator.go` |
| P2-T6 | 5 个模块测试用例表整表“待补”，与已有测试脱节 | `docs/product/05-testing/cases/{api-test,container,file-parser,image,user-tenant}.md` |
| P2-T7 | CodeQL 不覆盖 dev 分支 PR，仍用 v2 action | `.github/workflows/codeql-analysis.yml:9-13,37-69` |
| P2-T8 | `pr-backend` 路径过滤漏掉迁移 SQL；`maven.yml` 不构建 keycloak listener | `pr-backend.yml:9-12`、`maven.yml:4-8` |
| P2-T9 | 无 JaCoCo/覆盖率阈值，前端无 `--coverage` | `backend/pom.xml`、`frontend/package.json:10` |

---

## 6. 建议的修复顺序

1. **止血（P0）**
   - 交付运维平台加认证并收敛 NodePort。
   - 修复 PM、API 测试、容器 WebSocket 的租户/项目隔离。
   - 移除 `05-pipeline-schema.sql` 的 DROP，统一迁移记录表。
2. **凭证与默认配置（P1-S3/S4/S5）**
   - 移除代码内默认密钥；kubeconfig 真正加密；停止挂载整个 `~/.kube`。
3. **接口契约（P1-B1~B7）**
   - 统一 `BizException + ResultCode` 与 HTTP 401/403；补齐 Postman。
4. **质量门禁（P1-T1~T6、P2-T9）**
   - 给 `pm-core` 引入测试依赖；加 JaCoCo 阈值；把集成测试纳入 CI；修正测试文档映射。
5. **前端与文档（P1-F1~F3、P1-DOC1/2、P2 各表）**
   - 图表 token、ConfirmDialog、shadcn 原语；补原型冻结与版本头。

## 7. 建议新增的自动化门禁

| 门禁 | 做法 |
|------|------|
| 迁移安全 | CI 检查历史 `.sql` 未被修改；禁止基线迁移出现 `DROP TABLE`；校验 `migrate.sh`/`migrate.py` 记录表一致 |
| 双模式一致性 | 启动一个测试用例分别用 external/embedded 建库，diff 表、列、索引、种子 |
| 覆盖率 | 后端 JaCoCo 按模块设最低阈值；前端 `vitest --coverage` 并在 PR 输出摘要 |
| 文档元信息 | 新增/修改 docs 时校验日期、版本、文首变更记录 |
| 接口文档 | 对比 Controller 映射与模块文档/Postman，缺失即失败 |
| 租户隔离 | 为 PM、API 测试、容器资源增加“跨租户访问应 403”的集成测试 |

## 8. 本次未验证项

- 未运行全量 `mvn test`、`npm test`、`tsc --noEmit`，未实测 CI 在 GitHub runner 的结果。
- 未启动服务做真实 HTTP 越权复现；容器 null-tenant 路径需构造“已认证但无可用租户”的用户确认。
- 未做 git 历史 secret 扫描、依赖 CVE 扫描、Helm 渲染与运行中集群验证。
- Kong file-log 是否实际写入 `Authorization` 为推测，需查看运行日志。
- 未逐页做原型视觉对比与像素 diff。

## 9. 实施后复核（2026-10-18，v0.2）

P0-0/2/3/4/5 已按 `docs/superpowers/features/20261018-p0-security-remediation/design.md` v0.6 实施：

| 项 | 结果 |
|----|------|
| P0-0 权限错误映射 | 已实施；server 定向测试 17/17 |
| P0-2 PM 守卫 | 已实施核心守卫；pm-core 10/10；MockMvc 全入口待补 |
| P0-3 API 测试隔离 | 已实施；api-test 1065/1065 |
| P0-4 容器隔离 | 已实施；container 73/73；WS/Argo 单测通过 |
| P0-5 迁移安全 | 已实施；迁移单测 12/12、门禁通过 |
| 全量后端 | `mvn -B -f backend/pom.xml clean verify` 通过 |
| 全量前端 | `npm test` 227 通过 / 1 skipped；`npm run build` 通过 |
| 接口文档/Postman | 已补 P0 变更接口与失败示例 |

**仍未实施**：P0-1 交付运维平台认证（用户明确“不加”），NodePort 无认证风险仍在。

**第二轮独立复验**：有条件通过。修复全局字段绑定、WS 缺租户提示、Helm 死回退、迁移锁内 checksum、16-* 精确匹配、Postman/文档路径与测试映射后，全量后端 `clean verify`、前端 `npm test/build`、迁移门禁/单测、3 个 Postman JSON 均通过；残余仅为非阻断文档/映射问题，已在 `changes.md` §7.3 记录。

**已知限制**：见 `changes.md` §5（PM MockMvc、部分容器集成测试、accessibleProjectIds 分块、Argo 前端无页面等）。
