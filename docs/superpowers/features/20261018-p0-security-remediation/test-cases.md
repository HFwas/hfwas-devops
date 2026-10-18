# P0 租户隔离与迁移安全修复 —— 测试用例

> 日期：2026-10-18
> 版本：v0.1
> 状态：随实现维护
> 关联：[design.md](./design.md) v0.6

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：按 design v0.6 的 AC 建立用例与自动化映射 |

---

> 自动化位置说明（2026-10-18 独立校验后）：P0-0/3/5 的映射已与代码对齐；P0-2 的 MockMvc 全入口测试、P0-4 的 Registry/ImageSearch/Monitor/Argo Controller 集成测试尚未落地，对应条目按“手工/待补”执行，不作为已自动化声明。

## 1. 测试环境与约定

- 后端：`mvn -B -f backend/pom.xml clean verify`；单模块用 `-pl <module> -am test`。
- 前端：`cd frontend && npm test && npm run build`。
- 迁移：`python3 -m unittest discover -s scripts/tests -p 'test_*.py'`；门禁 `bash scripts/check-migrations.sh`。
- 越权 fixture：两个租户（A/B）、每租户两个项目、普通用户与平台 admin 各一。
- 自动化位置格式：`文件::测试名`；未自动化用例写明手工步骤。
- 优先级：P0 必须自动化；P1 至少单测；P2 可手工。

## 2. P0-0 权限错误 HTTP 映射

| 编号 | 优先级 | 前置条件 | 步骤 / 输入 | 预期结果 | 自动化位置 |
|------|--------|----------|-------------|----------|-----------|
| TC-0-01 | P0 | 已构造 BizException(11005) | 调用 ExceptionAdvice.handleBizException | HTTP 403，body.code=11005 | `ExceptionAdviceTest::tenantForbiddenMapsTo403` |
| TC-0-02 | P0 | BizException(11010) | 同上 | HTTP 401，body.code=11010 | `ExceptionAdviceTest::tenantContextMissingMapsTo401` |
| TC-0-03 | P0 | BizException(20002) | 同上 | HTTP 403，body.code=20002 | `ExceptionAdviceTest::projectAccessDeniedMapsTo403` |
| TC-0-04 | P0 | BizException(20001) | 同上 | HTTP 404，body.code=20001 | `ExceptionAdviceTest::projectNotFoundMapsTo404` |
| TC-0-05 | P0 | BizException(10001) | 同上 | HTTP 200，body.code=10001 | `ExceptionAdviceTest::internalErrorStays200` |
| TC-0-06 | P0 | 非成员用户 + X-Tenant-Id | 请求受保护接口 | HTTP 403 + body.code=11005，不是 500 | `TenantContextFilterTest::nonMemberReturns403` |
| TC-0-07 | P0 | 无效 X-Tenant-Id | 请求受保护接口 | HTTP 400 + body.code=11006 | `TenantContextFilterTest::invalidTenantHeaderReturns400` |
| TC-0-08 | P1 | blob 下载返回 403 JSON | 前端 `getBlob` 请求 | 抛出 ApiError(20002/11005)，不退化为 INTERNAL_ERROR | `request.test.ts::parses a JSON blob error body` |

## 3. P0-2 PM 租户/项目归属

| 编号 | 优先级 | 前置条件 | 步骤 / 输入 | 预期结果 | 自动化位置 |
|------|--------|----------|-------------|----------|-----------|
| TC-2-01 | P0 | 用户 B、事项 A1 | `GET /pm/work-items/{A1}` | 403 + 20002 | `手工（MockMvc 待补）` |
| TC-2-02 | P0 | 用户 B、事项 A1 | `POST /pm/work-items/delete` | 403，数据不变 | `手工（MockMvc 待补）` |
| TC-2-03 | P0 | 用户 B、事项 A1 | `POST /pm/work-items/{A1}/transition` | 403，状态不变 | `手工（MockMvc 待补）` |
| TC-2-04 | P0 | 用户 B | `POST /pm/work-items/save` 改 A1 projectId 到 B 项目 | 403，projectId 不变 | `WorkItemServiceTest::updateCannotMoveAcrossProjects` |
| TC-2-05 | P0 | 用户 B | `POST /pm/work-items/page` 不带 projectId | 只返回 B 租户项目事项 | `手工（MockMvc 待补）` |
| TC-2-06 | P0 | 用户 B | `POST /pm/work-items/page` 带 A 项目 projectId | 403 | `手工（MockMvc 待补）` |
| TC-2-07 | P0 | 用户属于 A/B，当前选 A | 深链查 B 项目 page/export | 成功且只返回 B 数据 | `手工（MockMvc 待补）` |
| TC-2-08 | P0 | 用户 B、A 事项 | 评论列表/新增/计数/删除 | 403 | `手工（待补）` |
| TC-2-09 | P0 | 用户 B、A 事项 | 活动列表 | 403 | `手工（待补）` |
| TC-2-10 | P0 | 用户 B | `POST /pm/board` 带 A 项目 | 403 | `手工（MockMvc 待补）` |
| TC-2-11 | P0 | 用户 B | fields list/options/getById、layout get、workflow get/allowed、issue-type export、project-issue-types list | 403 | `手工（待补）` |
| TC-2-12 | P0 | 用户 B | 字段 delete-by-id 指向 A 字段 | 403，字段仍在 | `手工（待补）` |
| TC-2-13 | P0 | 用户 B | layout get/save 目标项目无布局 | 不读取/覆盖其它项目唯一布局 | `FieldLayoutServiceTest::noCrossProjectFallback` |
| TC-2-14 | P0 | 用户 B | import 到 A 项目 | 整批拒绝，无写入 | `手工（待补）` |
| TC-2-15 | P0 | 普通用户 | `/pm/meta/types/save` | 403 | `手工（MockMvc 待补）` |
| TC-2-16 | P0 | 普通用户 | 新建 project_id NULL 字段 | 400/403 | `手工（待补）` |
| TC-2-17 | P1 | 用户 B | `sort.field` 注入串 | 400/拒绝，不执行 SQL | `QueryEngineTest::unsupportedSortFieldIsRejected` |
| TC-2-18 | P0 | 用户 B | modules/fields/views update-by-id 改 projectId | 403，归属不变 | `手工（待补）` |

## 4. P0-3 API 测试项目隔离

| 编号 | 优先级 | 前置条件 | 步骤 / 输入 | 预期结果 | 自动化位置 |
|------|--------|----------|-------------|----------|-----------|
| TC-3-01 | P0 | 用户 B、A 定义 | `GET/PUT/DELETE /apitest/definitions/{A}` | 403 | `ApiDefinitionServiceTest::crossProjectForbidden` |
| TC-3-02 | P0 | 用户 B | `POST /apitest/definitions?userId=999` | userId 被忽略，createBy=当前用户 | `ApiDefinitionServiceTest::userIdFromContext` |
| TC-3-03 | P0 | 用户 B、A 分组 | `GET /apitest/groups/{A}` | 403 | `ApiGroupServiceTest::getDetailCrossProjectForbidden` |
| TC-3-04 | P0 | 用户 B | 定义/集合/环境 page 不带 projectId | 只返回可见项目；空集合空页 | `ApiTestIsolationTest::listScopedToAccessibleProjects` |
| TC-3-05 | P0 | 用户 B、A 集合 | 条目 add/batchAdd 引用 A definition | 403/跳过，不写入 | `CollectionItemServiceTest::definitionMustMatchCollectionProject` |
| TC-3-06 | P0 | 用户 B、A 环境 | debug execute 用 A environmentId | 403，不读取 A 变量 | `ApiDebugServiceTest::environmentMustMatchProject` |
| TC-3-07 | P0 | 用户 B、A 环境 | collection run 用 A environmentId | 403 | `CollectionRunServiceTest::environmentMustMatchCollectionProject` |
| TC-3-08 | P0 | 用户 B、A 运行记录 | `GET/DELETE /apitest/collections/runs/{A}` | 403 | `CollectionRunServiceTest::runIdScopedByCollectionProject` |
| TC-3-09 | P0 | 用户 B、A 历史 | `GET /apitest/debug-history/{A}`、by-definition、deleteBatch | 403 | `DebugHistoryServiceTest::crossProjectForbidden` |
| TC-3-10 | P0 | 用户 B | 历史 keyword 命中 A requestUrl | 不返回 A 记录 | `DebugHistoryServiceTest::keywordDoesNotBypassProject` |
| TC-3-11 | P0 | 用户 B | 环境 getVariableMap(A env) | 403 | `EnvironmentServiceTest::variableMapProjectGuard` |
| TC-3-12 | P1 | 前端 | 全局 grep `userId=` in api-test api | 0 处 | `npm run build` + 手工 grep |

## 5. P0-4 容器租户隔离

| 编号 | 优先级 | 前置条件 | 步骤 / 输入 | 预期结果 | 自动化位置 |
|------|--------|----------|-------------|----------|-----------|
| TC-4-01 | P0 | 用户 B、A 集群 | WS 日志握手 clusterId=A | 握手 403，不建立连接 | `ContainerWsAuthInterceptorTest::foreignClusterIsRejected` |
| TC-4-02 | P0 | 用户 B、A 集群 | WS Shell 握手 clusterId=A | 403 | `手工（Shell 路径待补）` |
| TC-4-03 | P0 | 已认证用户 | WS 无 tenantId query/claim | 拒绝，不回落默认租户 | `ContainerWsAuthInterceptorTest::missingTenantIsRejected` |
| TC-4-04 | P0 | 停用用户 | WS/HTTP 请求 | 401/403 | `ContainerWsAuthInterceptorTest::disabledUserIsRejected` |
| TC-4-05 | P0 | 正确租户/admin | WS 日志/Shell | 可连接 | `ContainerWsAuthInterceptorTest::validTenantAndClusterIsAccepted` |
| TC-4-06 | P0 | tenantId=null | `ClusterService.getById(id,null)` | BizException 11010 | `ClusterServiceSecurityTest::nullTenantIsRejected` |
| TC-4-07 | P0 | 用户 B | Registry page/findById/create/listByTenant、ImageSearch | 跨租户 403/无租户 401 | `RegistryServiceSecurityTest::foreignTenantRegistryIsForbidden`、`手工（待补）` |
| TC-4-08 | P0 | 用户 B | HelmChart/HelmRelease 跨租户 | 403 | `手工（待补）`、`手工（待补）` |
| TC-4-09 | P0 | 用户 B | MonitorController 跨租户 | 403 | `手工（待补）` |
| TC-4-10 | P0 | 普通用户 | `/argo/**` | 403 | `ArgoAdminInterceptorTest::nonAdminIsForbidden` |
| TC-4-11 | P1 | 前端非 admin | 产品或菜单/工作台 | 不显示 Argo | `products.test.ts::hides adminOnly products from non-admins`、`navigation.test.ts::hides Argo from non-admins`、`WorkbenchPage 手工验证` |
| TC-4-12 | P0 | 前端 PodLog/PodShell | 打开 WS | URL 带 tenantId | `PodLogPanel.test.tsx::sends tenantId in the log websocket url`、`PodShell.test.tsx::sends tenantId in the shell websocket url` |

## 6. P0-5 迁移安全

| 编号 | 优先级 | 前置条件 | 步骤 / 输入 | 预期结果 | 自动化位置 |
|------|--------|----------|-------------|----------|-----------|
| TC-5-01 | P0 | 全新库 | 执行 01-16 | 19 行 kind（18 enabled + IMAGE_COSIGN enabled=0）、DT_FAIL_ON options、8 模板、最终 default_command | `test_migrate.py::test_fresh_database_seeds` |
| TC-5-02 | P0 | 已填充库 | 连续执行两次 01-16 | 第二次零改写；用户 command_template/default_command/pipeline_job.command 不变 | `test_migrate.py::test_rerun_preserves_user_data` |
| TC-5-03 | P0 | 用户主动清空 command_template | 重跑 05/16 | 仍为空，不被填充 | `test_migrate.py::test_empty_template_preserved` |
| TC-5-04 | P0 | 旧默认模板库 | 执行 16-* | 旧默认值升级；用户编辑不变；重跑幂等 | `test_migrate.py::test_upgrade_old_defaults` |
| TC-5-05 | P0 | 缺 pipeline_id/identity_key 旧表 + 数据 | 执行迁移 | 新表创建成功，legacy 表保留原数据 | `test_migrate.py::test_legacy_dependency_component_rebuild` |
| TC-5-06 | P0 | duplicate column | 执行含重复列的迁移 | 后续语句继续执行，checksum 正常写入 | `test_migrate.py::test_duplicate_column_continues` |
| TC-5-07 | P0 | 非 duplicate 错误 | 执行失败迁移 | 不写 checksum，退出码非 0 | `test_migrate.py::test_failure_does_not_record_checksum` |
| TC-5-08 | P0 | 旧 schema_migrations 记录 | 切换执行器 | 合并到 _schema_migrations，不重复执行 | `test_migrate.py::test_legacy_ledger_merged` |
| TC-5-09 | P0 | 含 DROP/UPDATE 的旧 05 | 执行新 migrate.py | 硬保护拒绝 | `test_migrate.py::test_destructive_statement_rejected` |
| TC-5-10 | P0 | 两个进程并发 | 同时执行 migrate.py | 一方成功，另一方等待/安全失败；integrity_check ok | `test_migrate.py::test_concurrent_migration` |
| TC-5-11 | P0 | embedded/external | 建两库比对 | kind、DT 参数、模板、IMAGE_COSIGN 一致 | `test_migrate.py::test_embedded_external_parity` |
| TC-5-12 | P1 | 任意目录 | `migrate.sh DB SQL_DIR` | 正常执行 | `test_migrate.py::test_shell_wrapper_from_any_dir` |
| TC-5-13 | P0 | PR 改迁移 SQL | CI | 迁移 job 执行；UPDATE 仅 16-* 允许 | `scripts/check-migrations.sh` + CI |

## 7. 手工验证

| 编号 | 步骤 | 预期 |
|------|------|------|
| MV-01 | staging 执行 DB `.backup` + `PRAGMA integrity_check`，升级后抽样用户模板 | 数据保留，校验 ok |
| MV-02 | staging 演练 Helm rollback 到升级前版本 | 按 runbook 先恢复备份；不得直接 rollback |
| MV-03 | 两租户浏览器手工走查 PM/API 测试/容器/Argo | 非 admin 看不到 Argo；跨租户 403 |
| MV-04 | 前端 WS 日志/Shell 打开 | 正常连接，localStorage tenantId 生效 |
