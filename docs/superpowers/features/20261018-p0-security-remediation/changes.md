# P0 租户隔离与迁移安全修复 —— 代码变更文档

> 日期：2026-10-18
> 版本：v0.2
> 状态：实施完成，独立复验有条件通过（残留为非阻断文档/测试映射问题）
> 关联：[design.md](./design.md) v0.6、[test-cases.md](./test-cases.md) v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 开工骨架：列出计划文件、设计映射、原型豁免与验证计划 |
| v0.2 | 2026-10-18 | 实施完成：P0-0/2/3/4/5 全部落地；补实际改动、偏差、验证与两轮独立校验结果 |

---

## 1. 实施顺序

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | P0-0 权限错误 HTTP 映射 | 已完成 |
| 2 | P0-5 迁移安全 + 双模式 | 已完成 |
| 3 | P0-2 PM 守卫 | 已完成 |
| 4 | P0-3 API 测试隔离 | 已完成 |
| 5 | P0-4 容器隔离 | 已完成 |
| 6 | 文档、Postman、最终验证 | 已完成 |

## 2. 计划改动文件

### 2.1 P0-0

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `backend/server/.../ApiErrorWriter.java` | §4.1/§4.2 | 补 401/400/403/404 映射 |
| `backend/server/.../ExceptionAdvice.java` | §4.1 | `ResponseEntity` 动态状态 |
| `backend/server/.../TenantContextFilter.java` | §4.1 | 同时捕获 IAE/BizException |
| `backend/user-core/.../DefaultTenantAccessValidator.java` | §4.1 | 改抛 BizException |
| `frontend/src/shared/api/request.ts` | §4.1 | blob 错误拦截器 |
| `backend/server/src/test/.../ExceptionAdviceTest.java` | §4.3 | 新增 |

### 2.2 P0-5

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `deploy/charts/backend/files/db/05-pipeline-schema.sql` | §8.1 | 删 DROP/UPDATE，种子进 INSERT，补 IMAGE_COSIGN |
| `deploy/charts/backend/files/db/16-upgrade-task-template-seeds.sql` | §8.1.3 | 新增 |
| `deploy/charts/backend/files/db/migrate.py` | §8.1.4/§8.3 | 逐语句、事务、legacy、记录表、硬保护 |
| `deploy/charts/backend/files/db/migrate.sh` | §8.3 | 薄包装 |
| `backend/server/src/main/resources/db/pipeline-schema.sql` | §8.2 | 零 UPDATE，补 KUBECTL/DT/IMAGE_COSIGN |
| `backend/server/.../SqliteSchemaInitializer.java` | §8.2 | legacy 备份重建 |
| `scripts/check-migrations.sh` | §8.6 | 新增 |
| `scripts/migrations.lock` | §8.6 | 新增 |
| `scripts/tests/test_migrate.py` | §8.3/§8.6 | 新增 |
| `.github/workflows/pr-backend.yml`、`maven.yml` | §8.6 | 迁移 job |

### 2.3 P0-2

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `backend/pm-core/.../ProjectService.java` | §5.1 | `assertProjectAccess`、getById 收紧 |
| `backend/pm-core/.../WorkItemService.java` | §5.3/§5.4 | get/page/save/delete/transition/links |
| `backend/pm-core/.../WorkItemCommentService.java` | §5.3 | 评论守卫 |
| `backend/pm-core/.../WorkItemActivityService.java` | §5.2 | 活动守卫 |
| `backend/pm-core/.../WorkItemImportExportService.java` | §5.3 | scope 守卫 |
| `backend/pm-core/.../QueryEngine.java`、`JsonSqlDialect.java` | §5.4 | 租户过滤、排序白名单 |
| `backend/pm-core/.../SavedViewService.java`、`ProjectModuleService.java`、`FieldDefinitionService.java`、`FieldLayoutService.java`、`StatusDefinitionService.java`、`IssueTypeSchemeService.java`、`ProjectIssueTypeService.java` | §5.2/§5.3 | 全入口守卫 |
| `backend/pm-core/pom.xml` | §5.7 | 测试依赖 |

### 2.4 P0-3

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `backend/user-api/.../ProjectAccessValidator.java` | §6.1 | 新增 SPI |
| `backend/pm-core/.../PmProjectAccessValidator.java` | §6.1 | 新增实现 |
| `backend/api-test-core/.../controller/*.java`、`service/*.java` | §6.3 | 删 userId、全入口守卫 |
| `backend/api-test-core/src/test/.../BaseApiTest.java` | §6.7 | 测试 Bean |
| `frontend/src/modules/api-test/**/api/*.ts` 及调用方 | §6.5 | 删 userId |

### 2.5 P0-4

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `backend/container-core/.../SecurityHelper.java` | §7.2 | requireTenantId |
| `backend/container-core/.../ClusterService.java` | §7.2 | null/mismatch 拒绝 |
| `backend/container-core/.../RegistryService.java`、`ImageSearchService.java`、`HelmChartService.java`、`HelmReleaseService.java` | §7.2 | fail closed |
| `backend/container-core/.../MonitorController.java`、`MonitorService.java` | §7.1/§7.2 | requireTenantId、删死代码 |
| `backend/container-core/.../ws/*.java` | §7.3 | 握手 cluster/停用用户、attributes Long |
| `backend/container-core/.../ArgoWorkflowController.java`、`ArgoWorkflowService.java` | §7.2 | admin-only |
| `frontend/src/modules/container/components/PodLogPanel.tsx`、`PodShell.tsx` | §7.3 | WS tenantId |
| `frontend/src/shared/console/products.ts`、`navigation.ts`、`WorkbenchPage.tsx` | §7.4 | Argo 隐藏 |

### 2.6 文档

| 文件 | 设计章节 | 动作 |
|------|----------|------|
| `docs/backend/api_spec.md` + `.postman_collection.json` | §12.1 | 删 userId、补 401/403 |
| `docs/pm/pm-api.md` + `.postman_collection.json` | §12.1 | 403/404 |
| `docs/container-platform/container-api.md` + `.postman_collection.json` | §12.1 | 新建 |
| `AGENTS.md`、数据库/部署文档 | §8.7 | 执行器、编号、Recreate、busy_timeout |

## 3. 原型评审豁免

按 `prototype-first-standard.md` §2.2，本轮前端改动无新增页面/布局/交互层：

| 改动 | 判定 | 理由 |
|------|------|------|
| API 测试删 userId | 豁免 | 请求参数变化 |
| 容器 WS 加 tenantId | 豁免 | URL 参数变化 |
| canMutate 权限来源 | 豁免 | 无布局变化 |
| Argo 产品/菜单/卡片 adminOnly | 豁免 | 仅可见性变化，无新页面；当前无 argo 路由 |

不适用“页面改版”原型门禁；若实现中发现按钮可见性/布局变化，补无权限态截图。

## 4. 实际改动

> 随阶段补全。每阶段记录文件、行为变化、计划外偏差。

### 4.1 P0-0（已完成）

| 文件 | 实际改动 |
|------|----------|
| `backend/server/.../ApiErrorWriter.java` | `resolveHttpStatus` 补 11010/11006/12012/20002/30005/30202/21001/30001 |
| `backend/server/.../ExceptionAdvice.java` | 四个 handler 改为 `ResponseEntity`，状态由 `resolveHttpStatus` 决定 |
| `backend/server/.../TenantContextFilter.java` | 分别捕获 `BizException` 与 `IllegalArgumentException`，使用动态状态 |
| `backend/user-core/.../DefaultTenantAccessValidator.java` | 非成员改抛 `BizException(TENANT_FORBIDDEN)` |
| `frontend/src/shared/api/request.ts` | 错误拦截器改 async，Blob 错误先读 text 再解析；403/401 兜底 |
| `backend/server/src/test/.../ApiErrorWriterTest.java` | 新增 7 条映射测试 |
| `backend/server/src/test/.../ExceptionAdviceTest.java` | 新增 6 条响应测试 |
| `backend/server/src/test/.../TenantContextFilterTest.java` | 新增 2 条 filter 测试 |

验证：`mvn -B -f backend/pom.xml -pl server -am test -Dtest=ApiErrorWriterTest,ExceptionAdviceTest,TenantContextFilterTest` → 17/17 通过；`npx tsc --noEmit` 通过。

### 4.2 P0-5（已完成）

| 文件 | 实际改动 |
|------|----------|
| `deploy/charts/backend/files/db/05-pipeline-schema.sql` | 删除 DROP 与 11 条 UPDATE；8 个 command_template、DT_FAIL_ON options、DEPENDENCY_ANALYSIS default_command 并入 INSERT；新增 IMAGE_COSIGN（enabled=0） |
| `deploy/charts/backend/files/db/16-upgrade-task-template-seeds.sql` | 新增：旧库补列 + 精确匹配旧 default_command/DT options 的 UPDATE（唯一允许 UPDATE 的迁移） |
| `deploy/charts/backend/files/db/migrate.py` | 重写：逐语句、BEGIN IMMEDIATE、duplicate 仅 ALTER、失败不写 checksum、`_schema_migrations`、旧表合并、legacy 重建、DROP/UPDATE 硬保护、busy_timeout |
| `deploy/charts/backend/files/db/migrate.sh` | 改为 `migrate.py` 薄包装，支持位置参数 |
| `backend/server/src/main/resources/db/pipeline-schema.sql` | 零 UPDATE；补 KUBECTL/DEPENDENCY_TRACK/IMAGE_COSIGN、6 个 DT 参数、8 个模板 |
| `backend/server/.../SqliteSchemaInitializer.java` | 旧 dependency_component 先备份 legacy 再重建；检查 6 个必需列 |
| `deploy/charts/backend/templates/configmap.yaml`、`deployment.yaml` | datasource URL 增加 `busy_timeout=5000` |
| `deploy/charts/backend/values.yaml` | strategy 改 `Recreate` |
| `backend/server/src/main/resources/application.yml` | embedded URL 增加 `busy_timeout=5000` |
| `scripts/check-migrations.sh` | 新增：checksum lock、破坏性语句拦截、UPDATE 白名单 |
| `scripts/migrations.lock` | 新增：01-16 SHA-256 |
| `scripts/tests/test_migrate.py` | 新增 11 条迁移测试 |
| `.github/workflows/pr-backend.yml`、`maven.yml` | 增加迁移 paths 与 job/step |
| `AGENTS.md`、data-dictionary、standard、deployment-ops、tables/pipeline.md | 执行器/编号/零 UPDATE/Recreate/备份 runbook |

验证：
- `python3 deploy/.../migrate.py` 全新库 → 19 kind/18 enabled/6 DT 参数，`integrity_check=ok`。
- 重跑 → 全部 skip，用户自定义 `command_template`/空模板/default_command 保留。
- 模拟 05 checksum 变化 → 05 重跑，用户值保留。
- 旧 dependency_component → legacy 表保留数据，新表重建成功。
- `python3 -m unittest discover -s scripts/tests` → 14/14 通过。
- `bash scripts/check-migrations.sh` → 13 个迁移通过。
- `mvn -B -f backend/pom.xml -pl server -am test` → 27/27 通过。

### 4.3 P0-2（已完成核心守卫，MockMvc 待补）

| 文件 | 实际改动 |
|------|----------|
| `ProjectService.java` | 新增 `assertProjectAccess`/`accessibleProjectIds`；`resolveAccessContext`/`getById`/`requireTenantId` 改抛 `BizException` |
| `WorkItemService.java` | page/getById/save/delete/transition/links/listByStatus 全入口守卫；save 源+目标项目校验并禁止改归属 |
| `QueryEngine.java` | `execute(spec, tenantId)`；无 projectId 按租户子查询 `del_flag=0`；排序字段白名单 |
| `WorkItemCommentService.java` | 事项/评论/批量计数守卫 |
| `WorkItemActivityService.java` | 活动列表守卫 |
| `WorkItemImportExportService.java` | `validateScope` 守卫；QueryEngine 传租户 |
| `SavedViewService.java` | 保存/列表/删除守卫；userId 服务端赋值 |
| `ProjectModuleService.java` | tree/flat/save/delete 守卫；update 旧项目校验 |
| `FieldDefinitionService.java` | list/catalog/options/getById/save/delete/addToType/removeFromType 守卫；禁止新建全局字段 |
| `FieldLayoutService.java` | get/save 守卫；删除 `byType.size()==1` 跨项目回退 |
| `StatusDefinitionService.java` | workflow 读/写/重置守卫；allowed 按事项项目校验 |
| `IssueTypeSchemeService.java` | export/import/preview 守卫 |
| `ProjectIssueTypeService.java` | list/save 守卫 |
| `PmMetaController.java` | board 一次校验；全局类型写入口仅 admin |
| `pm-core/pom.xml` | 增加 starter-test + surefire 3.6.0 |
| `ProjectServiceTest.java`、`QueryEngineTest.java` | 新增 7 条守卫/白名单测试 |

验证：`mvn -B -f backend/pom.xml -pl pm-core -am test` → 7/7 通过。

### 4.4 P0-3（已完成）

| 文件 | 实际改动 |
|------|----------|
| `ApiTestAccessGuard.java` | 新增统一守卫：`requireUserId`/`assertProject`/`accessibleProjectIds`/`requireTenantId` |
| 8 个 Controller | 删除实际 17 处 `@RequestParam Long userId`；身份改从 `CurrentUserAccessor` |
| `ApiDefinitionService`、`ApiDefinitionParamService`、`ApiDefinitionResponseService` | page/get/create/update/delete/publish/deprecate/revert 守卫；列表按可见项目；createBy 服务端赋值 |
| `ApiGroupService` | create/update/delete/tree/getDetail 守卫；getDetail 先加载后校验 |
| `CollectionService`、`CollectionFolderService`、`CollectionItemService` | 集合/文件夹/条目守卫；条目 definition 必须与集合同项目 |
| `CollectionRunService` | 集合+环境项目一致；写入 run.projectId/environmentId；runId 按 run→collection 反查 |
| `EnvironmentService` | page/list/detail/create/update/delete/getVariableMap 守卫 |
| `ApiDebugService`、`ApiDebugExecuteDTO` | projectId 必填；definition/environment 项目一致 |
| `DebugHistoryService` | page keyword 用 `.and(...)` 包裹；detail/by-definition/delete/deleteBatch 守卫 |
| `BaseApiTest`、`TestProjectAccessValidator`、`TestCurrentUserAccessor`、`TestExceptionAdvice` | 测试 Bean 与 401/403/404 映射 |
| 新增 `ApiTestIsolationTest`、`CollectionFolderServiceTest`、`CollectionItemServiceTest`、`CollectionRunServiceTest`、`DebugHistoryServiceTest` | 隔离用例 |
| `frontend/src/modules/api-test/**/api/*.ts`、3 个页面、`createRequestInCollection.ts` | 删除 userId 参数与状态 |

验证：
- `mvn -B -f backend/pom.xml clean verify` → api-test 1065 条全部通过（含新增隔离用例）。
- `cd frontend && npm test` → 227 通过 / 1 skipped。
- `npm run build` → 通过。
- `npx tsc --noEmit` → 通过。
- `grep @RequestParam.*userId` → 0 处。

已知限制：
- 列表过滤仍使用 `IN (accessibleProjectIds)`；项目数超过 SQLite 999 参数上限时需分块（后续优化）。
- 存量 `run.projectId = null` 反查分支已实现，但测试库 `project_id NOT NULL`，未用 fixture 覆盖。

### 4.5 P0-4（已完成，HTTP/WS 集成测试待补）

| 文件 | 实际改动 |
|------|----------|
| `SecurityHelper.java` | 新增 `requireTenantId()` |
| `ClusterService.java` | `getById` null→11010、跨租户→30005；`page` 强制租户；`getByIdInternal`→`getByIdForSystem` |
| `RegistryService.java` | create/page/findById/listByTenant 全部 fail closed |
| `DeployFromImageService.java` | requireTenantId |
| `HelmChartService.java`、`HelmReleaseService.java` | 删除 `PLATFORM_TENANT_ID` 回落，改 requireTenantId |
| 容器所有 Controller | `currentTenantId()` → `requireTenantId()` |
| `ArgoAdminInterceptor.java`、`ContainerWebMvcConfig.java` | `/argo/**` 仅平台 admin |
| `ContainerWsAuthInterceptor.java` | 解析平台 SysUser、停用用户、租户成员、集群归属；attributes 写 Long |
| `LogTailHandler.java`、`ResourceWatchHandler.java`、`PodShellWebSocketHandler.java` | 从 attributes 取 Long tenantId；改用 `getById(clusterId, tenantId)` |
| `MonitorService.java` | 删除 `getPrometheusUrlInternal` 死代码 |
| `PodLogPanel.tsx`、`PodShell.tsx` | WS URL 带 tenantId |
| `products.ts`、`ProductSwitcher.tsx`、`navigation.ts`、`WorkbenchPage.tsx` | Argo `adminOnly` + 产品/菜单/卡片过滤 |
| 测试 | `ClusterServiceSecurityTest`（3）、`ArgoAdminInterceptorTest`（2）、`ContainerWsAuthInterceptorTest`（4）；Helm 测试注入固定租户 |

验证：
- `mvn -B -f backend/pom.xml -pl container-core -am test` → 71/71 通过。
- `npx vitest run` 容器/console 定向 → 14/14 通过。
- `npx tsc --noEmit` → 通过。

### 4.6 文档与 Postman（已完成）

| 文件 | 实际改动 |
|------|----------|
| `docs/backend/api_spec.md` | 升 v0.2；删除 23 处 userId 路径参数；新增 P0 隔离与 401/403/404 契约 |
| `docs/backend/api_spec.postman_collection.json` | 新增：定义/分组/集合/环境/调试/历史代表接口，正常+失败示例 |
| `docs/pm/pm-api.md` | 升 1.4；补变更记录与 P0 项目归属/403/404 说明 |
| `docs/pm/pm-api.postman_collection.json` | 新增：项目/事项/看板代表接口，正常+失败示例 |
| `docs/container-platform/container-api.md` | 新增：租户隔离、WS 握手、Argo admin、代表 HTTP 接口 |
| `docs/container-platform/container-api.postman_collection.json` | 新增：集群/Registry/Helm/Monitor 代表接口，正常+失败示例 |
| `docs/product/04-api/api-overview.md` | 升 v0.2；补权限错误 HTTP 状态表 |
| `docs/backend/error-code-design.md` | 升 1.1；补变更记录与 P0 状态映射 |
| `docs/product/04-api/api-module-matrix.md` | 升 v0.3；PM/容器/API 测试 Postman 标为已提供 |
| `AGENTS.md`、数据库/部署文档 | 已在 P0-5 更新 |

验证：3 个 Postman collection 均通过 `python3 -m json.tool`。

## 5. 计划外偏差

> 实施中记录：原因、影响、是否回写 design.md。

| 偏差 | 原因 | 影响 | 是否回写 design.md |
|------|------|------|--------------------|
| API 测试列表仍用 `IN (accessibleProjectIds)`，未做 >900 分块 | api-test 测试库没有 `pm_project` 表，子查询方案会破坏测试上下文；分块需要重构 5 个列表查询 | 项目数超过 SQLite 参数上限时列表会失败 | 已在 progress 记录；design §6.1 的“分块”尚未落地 |
| `getByIdForSystem` 保留在 `ClusterService` | 拆 `ClusterSystemAccess` 会扩大改动面；当前仅启动自检调用 | 仅靠 Javadoc/调用点约束，不是编译期隔离 | 已在 progress 记录；design §7.2 的“移到独立类”未落地 |
| PM 全入口 MockMvc 未实现 | 缺少 `server` 测试 profile/双租户 fixture；优先保证 service 层守卫 | AC-2.1/2.2/2.4 仅由 10 条核心单测覆盖，其余为手工 | 已在 test-cases 标注；design §5.7 的 MockMvc 计划未完全落地 |
| P0-4 Registry/ImageSearch/Monitor/Argo Controller 集成测试未实现 | 时间优先给核心 fail-closed 代码；Argo 用拦截器单测覆盖 | 这些入口的 HTTP 403 仅靠代码审查/手工验证 | 已在 test-cases 标注 |
| 16-* 不升级 `command_template` | 空模板是用户主动选择 DEFAULT_TEMPLATE，无法与旧默认区分；历史库已有旧 05 写入的模板 | 旧库不会自动获得新模板；需要时另开迁移 | 已在 16-* 文件头说明 |
| legacy 重建分两个事务 | 事务 1 备份 legacy 并提交；事务 2 DROP + 删除 05 记录并提交；05 随后在自身事务重建 | 若 05 失败，原表不在但 legacy 保留，下次启动重建；不是“回滚恢复原表” | 需在 design §8.1.4 标注实际语义 |
| `merge_legacy_ledger` 直接复制记录 | 完整“表/列/种子”校验成本高；`verify_known_schema` 在所有迁移后执行 | 旧记录中的部分执行问题会在迁移后校验时暴露，而不是合并前 | design §8.3 的“合并前校验”未完全落地 |
| 前端 Argo 无 `/argo` 页面/路由 | 仓库当前没有 Argo 页面，新增占位页无实际价值 | admin 入口落到 `*` 兜底；非 admin 已隐藏 | 已在 progress 记录 |
| 全局字段绑定禁止所有角色 | 比 design §5.5 “仅 admin”更严格，避免全局字段被改写 | admin 也不能改绑定；如需放开需改设计 | 需回写 design §5.5 |

## 6. 验证结果

> 只写真实执行过的命令与结果。

| 阶段 | 命令 | 结果 |
|------|------|------|
| P0-0 | `mvn -B -f backend/pom.xml -pl server -am test -Dtest=ApiErrorWriterTest,ExceptionAdviceTest,TenantContextFilterTest` | 17/17 通过 |
| P0-0 前端 | `cd frontend && npx tsc --noEmit` | 通过 |
| P0-5 | `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` | 14/14 通过 |
| P0-5 门禁 | `bash scripts/check-migrations.sh` | 13 个迁移通过 |
| P0-5 后端 | `mvn -B -f backend/pom.xml -pl server -am test` | 27/27 通过 |
| P0-2 | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 10/10 通过 |
| P0-3 | `mvn -B -f backend/pom.xml clean verify`（api-test 模块） | 1065/1065 通过 |
| 全量后端 | `mvn -B -f backend/pom.xml clean verify` | 10 个模块全部通过 |
| 全量前端 | `cd frontend && npm test && npm run build` | 227 通过 / 1 skipped；构建通过 |
| Postman JSON | `python3 -m json.tool` × 3 | 通过 |
| P0-4 | `mvn -B -f backend/pom.xml -pl container-core -am test` | 73/73 通过 |
| P0-4 前端 | `npx vitest run` 容器/console 定向 + `tsc --noEmit` | 14/14 通过；tsc 通过 |
| 前端 | `cd frontend && npm test && npm run build` | 227 通过 / 1 skipped；构建通过 |

## 7. 独立 agent 校验结果

> 实施完成后按 AGENTS 第 7 步执行；未校验不得声称完成。

### 7.1 第一轮（2026-10-18）

- **结论**：不通过（需修复后复验）。
- **核心代码**：P0-0/3/4/5 的安全实现通过抽查；后端 `clean verify`、前端 `npm test/build`、迁移门禁/单测均通过。
- **主要问题**：
  1. `test-cases.md` 中 P0-2/P0-4/P0-5 多个 `文件::测试名` 不存在或名称不符；
  2. Postman/container 文档多处方法/路径与代码不符；
  3. 16-* 未覆盖 `command_template`，`default_command` 用 `instr(' -q')` 而非精确匹配；
  4. `merge_legacy_ledger` 无合并前校验、`apply_file` 未在锁内二次校验 checksum；
  5. legacy 重建事务语义与 design 不一致；
  6. 全局字段 `addToType` 允许 admin 绑定，与“只读”冲突；
  7. 前端 WS 缺 tenantId 时静默省略；
  8. `changes.md` 数量/偏差记录不完整。

### 7.2 修复（2026-10-18）

- 全局字段绑定改为任何角色禁止修改。
- 前端 WS 缺 tenantId 时提示先选择租户，不再静默。
- `HelmReleaseService.tenantId()` 删除死回退。
- `apply_file` 在 `BEGIN IMMEDIATE` 内读取 checksum。
- 16-* 的 `default_command` 改为精确匹配旧值；明确不升级 `command_template`。
- 新增 `test_upgrade_old_defaults`、`WorkItemServiceTest`、`FieldLayoutServiceTest`、`RegistryServiceSecurityTest`、`request.test.ts`。
- Postman/container 文档方法与路径按代码修正；PM 20001/404 表述修正；data-dictionary 记录表描述修正。
- `test-cases.md` 映射改为实际测试名，未落地项标“手工/待补”。
- `changes.md` §5 记录全部已知偏差；复核报告升 v0.2 并追加 §9。

### 7.3 第二轮复验（2026-10-18）

- **结论**：有条件通过；4 个非阻断残留已修复。
- **已修复**：container Helm releases 路径、Python 缓存跟踪与 `.gitignore` negation、`changes.md` 版本/陈旧行、TC-5-03/5-06/4-11/4-12 测试映射、复核报告 224→227。
- **新增测试**：`test_empty_template_preserved`、`test_duplicate_column_continues`；迁移单测 14/14。
- **最终验证**：`mvn -B -f backend/pom.xml clean verify` SUCCESS（10 模块）；前端 227 通过 / 1 skipped、build 成功；迁移门禁 13 个通过、单测 14/14；3 个 Postman JSON 有效。
- **剩余非阻断**：PM MockMvc 全入口、部分容器集成测试、`accessibleProjectIds` 分块、Argo 前端无页面，详见 §5。
