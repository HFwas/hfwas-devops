# P0 租户隔离与迁移安全修复 —— 代码变更规格（Spec）

> 日期：2026-10-18
> 版本：v0.6
> 状态：已通过最终聚焦评审（v0.5 无 Blocker；v0.6 仅修 4 条 Major）
> 关联审查：[20261018-code-review-standards-compliance.md](../../review/20261018-code-review-standards-compliance.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：定义 P0-1～P0-5 |
| v0.2 | 2026-10-18 | 按首轮评审重写：P0-1 移出、纳入 401/403、P0-5 条件化 UPDATE、容器一次修完 |
| v0.3 | 2026-10-18 | 按二轮评审重写：05 零 UPDATE、补 PM/API/容器入口、WS 握手、embedded 同步 |
| v0.4 | 2026-10-18 | 按三轮评审与用户四项决策定稿：零 UPDATE + 16-*；Argo admin-only + 前端隐藏；PM 禁止改归属/禁止新建全局字段；补齐双模式种子；修 filter 500、work-item GET、字段删除、布局回退、busy_timeout、文档落点、legacy 事务 |
| v0.5 | 2026-10-18 | 按第四轮聚焦评审修正：FieldLayout 回退写进正文；Argo 前端三层隐藏；legacy 事务 2 回滚语义；IMAGE_COSIGN enabled=0 与 AC 计数；blob 拦截器；11006/12012 映射；16-* 旧值清单与 DT_FAIL_ON；CI UPDATE 全量拦截；ApiGroup.getDetail 守卫 |
| v0.6 | 2026-10-18 | 最终验收通过后修 4 条 Major：§4.1 补 11006/12012→400；Argo 补 WorkbenchPage 卡片过滤并取消不存在的 argo 路由要求；迁移门禁只匹配语句开头；§8.2 明确 IMAGE_COSIGN enabled=0 |

---

## 1. 背景与目标

`20261018` 存量审查确认 5 个 P0。v0.1～v0.3 经三轮独立评审均不通过，暴露了错误假设与漏项。本版目标：

1. 修复 **P0-2 PM、P0-3 API 测试、P0-4 容器、P0-5 迁移**。
2. 覆盖**全部**按 id/projectId/clusterId 的入口，包括只读配置、delete-by-id、update-by-id。
3. 迁移做到 **05 零 UPDATE**：历史库不因重跑被改写；旧默认模板升级走独立的 `16-*`。
4. 双模式（embedded/external）种子一致。
5. 每条验收标准可自动化，测试基础设施真实可运行。

## 2. 范围 / 非目标 / 用户决策

### 2.1 用户决策（2026-10-18）

| 决策 | 选择 | v0.4 落实 |
|------|------|-----------|
| 05 策略 | **零 UPDATE + 16-* 升级** | §8.1 / §8.2；05 只 CREATE/INSERT，16-* 精确匹配旧默认模板 |
| Argo | **admin-only + 前端隐藏** | §7.2 / §12.3 |
| PM 归属 | **禁止改 project_id + 禁止新建全局字段** | §5.3 / §5.5 |
| 双模式种子 | **本轮补齐差异** | §8.2；补 KUBECTL、6 个 DT 参数、IMAGE_COSIGN |
| 权限错误 HTTP | 纳入本 P0 | §4 |
| 交付平台认证 | 不加 | §9，记录残余风险 |

### 2.2 范围

| 编号 | 问题 |
|------|------|
| P0-0 | 权限类错误统一 HTTP 401/403/404 |
| P0-2 | PM 全部读/写/删除入口的租户/项目归属校验 |
| P0-3 | API 测试全部项目隔离入口、环境变量跨项目、运行记录归属 |
| P0-4 | 容器 WS + HTTP（Cluster/Registry/ImageSearch/Helm/Monitor/Argo）fail closed |
| P0-5 | 05 零 UPDATE + 16-* 升级 + 旧 schema 重建 + 迁移器修正 + 双模式一致 |

### 2.3 非目标

- **P0-1 交付运维平台认证**：用户明确“不加”，本轮不改 `delivery-platform/`。
- P1-S3/S4/S5：默认加密密钥、kubeconfig 明文、`~/.kube` 挂载。
- P1-D1：embedded `sys_login_log` 每次启动 DROP。
- P1-B1/B3～B7：其它错误码与 Postman 全量补齐。
- P1-T1～T6：全量测试与覆盖率门禁。
- P1-F1～F3、P2 全部。

### 2.4 已确认事实

- API 测试的 `projectId` 就是 PM 项目 ID（`ProjectSelect.tsx:27` 使用 `pmProjectApi.page`）。
- `pm_work_item` 无 `tenant_id`，租户归属经 `pm_project.tenant_id` 推导。
- 允许一次性修改 `05-pipeline-schema.sql`。

---

## 3. 总体原则

1. **fail closed**：租户/项目上下文缺失即拒绝；禁止 `tenantId != null` 才过滤。
2. **单一守卫**：按 id/projectId 的读写统一走 `ProjectService.assertProjectAccess` 或 `ProjectAccessValidator`。
3. **不信任客户端身份**：`userId` 从认证上下文解析。
4. **update/delete-by-id 必须校验旧实体**：先加载旧记录、校验旧 `projectId`；禁止改变 `project_id`。
5. **入口清单化**：`changes.md` 维护“入口 → 守卫 → 测试”表。
6. **迁移不改写历史数据**：05 零 UPDATE/DELETE/DROP；旧模板升级只在 `16-*` 显式进行。

---

## 4. P0-0 权限错误 HTTP 映射

### 4.1 改动

| 文件 | 改动 |
|------|------|
| `ApiErrorWriter.java` | 补映射：11001/11010→401；**11006/12012→400**；11005/20002/30005/30202/11003/11004→403；20001/21001/30001/10003/12001/13001→404；其它→200 |
| `ExceptionAdvice.java` | `BizException`/`IllegalArgumentException` handler 返回 `ResponseEntity<BaseResult<Void>>`，状态由 `resolveHttpStatus` 决定；`ApiTestException` 权限码同样映射，其它保持 200 |
| `TenantContextFilter.java` | **同时捕获 `IllegalArgumentException` 与 `BizException`**；用 `resolveHttpStatus` 写响应。`BizException` 不会进入 `@RestControllerAdvice`，必须在此处理，否则 500 |
| `DefaultTenantAccessValidator.java` | 改抛 `BizException(TENANT_FORBIDDEN)`；filter 已能捕获 |
| `frontend/src/shared/api/request.ts` | **响应错误拦截器 `:47-61` 改为 async**：`error.response.data` 为 Blob 时先 `await blob.text()` 再解析 JSON，避免 blob 请求的 403/业务错误退化为 INTERNAL_ERROR；401 触发登录；403 显示无权限。仅在 `getBlob/postBlob` 调用处改不够，必须改拦截器 |

### 4.2 映射决策表（唯一权威）

| 场景 | code | HTTP |
|------|------|------|
| 未登录 | 11001 | 401 |
| 租户上下文缺失 | 11010 | 401 |
| 无效租户 ID | 11006 | 400 |
| 无效租户角色 | 12012 | 400 |
| 非租户成员 | 11005 | 403 |
| 项目不存在/无权访问 | 20002 | 403 |
| 集群无权访问 | 30005 | 403 |
| 镜像仓库无权访问 | 30202 | 403 |
| 平台 admin 必需 | 11003/11004 | 403 |
| 项目不存在 | 20001 | 404 |
| 事项不存在 | 21001 | 404（仅内部/管理员；P0-2 对外统一 20002） |
| 其它业务错误 | 原 code | 200 |

### 4.3 验收

| ID | 标准 |
|----|------|
| AC-0.1 | 权限类 `BizException` 返回对应 401/403/404 |
| AC-0.2 | `TenantContextFilter` 的非成员请求返回 403 + body.code=11005，不是 500 |
| AC-0.3 | 既有 200 业务错误不回归 |
| AC-0.4 | blob 下载 403 时前端能读到业务码 |

---

## 5. P0-2 PM 租户/项目归属

### 5.1 统一守卫

```java
public void assertProjectAccess(Long projectId) {
    PmProject project = projectMapper.selectById(projectId);
    if (project == null || project.getTenantId() == null) {
        throw new BizException(ResultCode.PROJECT_ACCESS_DENIED); // 20002
    }
    UserContext ctx = currentUserAccessor.current()
            .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED)); // 11001
    tenantAccessValidator.assertAccess(ctx.getUserId(), ctx.getRole(), project.getTenantId());
}
```

- 不 catch-all 转换异常；`TenantAccessValidator` 改抛 `BizException`。
- `ProjectService.getById` 对 `tenantId == null` 或无权不再放行；读取项目也走守卫。
- `resolveAccessContext` 保留给深链，内部抛 `BizException`。

### 5.2 读入口（逐项）

| 入口 | 守卫 |
|------|------|
| `PmFieldDefinitionController` list / catalog / available / options / options-resolve / getById | 按 projectId/definitionId 解析项目后 assert；`project_id NULL` 的既有全局字段允许已登录用户只读 |
| `PmFieldDefinitionController` add-to-type / remove-from-type | 字段与事项类型项目一致；全局字段禁止修改绑定 |
| `PmFieldLayoutController` get / save | projectId assert；save 先加载旧布局；**`FieldLayoutService.findLayoutRow` 只保留 `project_id = ? AND type_code = ?` 精确匹配，删除 `byType.size() == 1 ? byType.get(0)` 跨项目回退** |
| `PmStatusWorkflowController` get / options / allowed / post-function-meta / transition-meta | projectId assert；`allowed` 只给 workItemId 时先加载事项并校验其项目 |
| `PmIssueTypeSchemeController` export / export-project / preview / import / import-project | projectId assert |
| `PmProjectIssueTypeController` list / save | projectId assert |
| `PmMetaController` board | **先** assert projectId，再读状态选项/事项 |
| `PmMetaController` types save/delete | 仅平台 admin（11003/403） |
| `PmSavedViewController` 全部 | projectId assert；`userId` 服务端赋值；delete 先加载 |
| `PmProjectModuleController` 全部 | projectId assert；update 先加载旧实体 |
| `PmWorkItemController` 全部 | 见 §5.3 |

### 5.3 写/删除/更新入口

| 入口 | 规则 |
|------|------|
| `GET /pm/work-items/{id}` | **加载事项 → `assertProjectAccess(item.projectId)`**；事项不存在/无权统一 20002/403 |
| `POST /pm/work-items/page` | `spec.projectId != null` → `assertProjectAccess`；为空 → 当前租户子查询；无租户 → 11010 |
| `/pm/work-items/save` | 新建：assert 请求 projectId；更新：加载旧事项 → assert 旧 projectId → **禁止 project_id 变化（403）** |
| `/pm/work-items/delete` / `{id}/transition` | 先加载 → assert → 操作 |
| `/pm/work-items/links/save` | source/target 两个事项都 assert |
| `/pm/work-items/{id}/links` | 先加载 → assert |
| comments list/save/counts/delete | 事项 assert；评论删除先加载评论再 assert 其事项；counts 逐 id assert 或按可见项目过滤 |
| activities | 先加载事项 → assert |
| import/export | `validateScope` assert；项目无权整批拒绝；行级业务错误维持部分成功 |
| `PmFieldDefinitionController` delete | **`deleteField(id)` 先加载字段 → assert 字段 projectId**；全局字段（project_id NULL）仅 admin 可删 |
| `removeFromType` | assert projectId；字段必须属于该项目或为全局只读；全局字段禁止改绑定 |
| modules / fields / views / layouts / workflows / issue-type schemes | update-by-id 加载旧实体 → assert 旧 projectId → 禁止 project_id 变化；create assert 请求 projectId |

### 5.4 QueryEngine

```java
execute(QuerySpec spec, Long tenantId)
```

| 条件 | 过滤 |
|------|------|
| `spec.projectId != null` | 调用方已 assert；只按 `project_id = ?`，不追加当前租户 |
| `spec.projectId == null` | `project_id IN (SELECT id FROM pm_project WHERE tenant_id = ? AND del_flag = 0)` |
| 无 projectId 且 tenantId null | 11010/401 |

- `WorkItemService.page` 必须负责显式 projectId 的 assert；不能把责任留给“调用方已 assert”。
- `sort.field` 白名单：系统字段 + 已加载自定义字段 key；`JsonSqlDialect` 同样校验。
- board 在 Controller 校验一次，不在 `listByStatus` 每状态重复。

### 5.5 全局字段与归属

- **禁止新建 `project_id NULL` 字段**：`FieldDefinitionService.save` 对 null projectId 返回 400/403。
- **既有全局字段只读兼容**：`getById/options` 允许已登录用户读取；update/delete 仅 admin；`addToType/removeFromType` 禁止任何角色修改绑定。
- **禁止改归属**：任何 update 请求的 `project_id` 与旧实体不同 → 403，数据不变（与 §5.3 一致，不再保留“双方都有权可搬移”）。

### 5.6 异常与边界

| 场景 | 响应 |
|------|------|
| 未登录 | 11001/401 |
| 租户上下文缺失 | 11010/401 |
| 项目不存在/无 tenant/非成员 | 20002/403 |
| 事项不存在/跨租户 | 20002/403 |
| update 改 project_id | 403 |
| 全局字段写/删 | 仅 admin；否则 403 |
| 导入项目无权 | 整批拒绝 |

### 5.7 测试

- `pm-core` 引入 `spring-boot-starter-test` + Mockito。
- `server` 新增 `application-test.yml`、`TestSecurityConfig`、SQLite 临时库、两租户/两项目/两用户 fixture。
- 必测：`GET /pm/work-items/{id}`、page 显式 projectId、字段 delete/removeFromType、layout 回退、全部只读入口、board、import/export、update 搬移、全局字段、排序白名单。
- `test-cases.md` 标注 `文件::测试名`。

### 5.8 验收

| ID | 标准 |
|----|------|
| AC-2.1 | `GET /pm/work-items/{id}` 跨租户 → 403 |
| AC-2.2 | update/delete 改变或操作他人实体 → 403，数据不变 |
| AC-2.3 | page 无 projectId 只返回当前租户；显式 projectId 越权 → 403；深链成功 |
| AC-2.4 | §5.2 全部读入口跨租户 → 403 |
| AC-2.5 | 字段 delete/removeFromType、layout 回退无跨租户 |
| AC-2.6 | 全局字段只读；禁止新建；写删仅 admin |
| AC-2.7 | 导入项目无权整批拒绝 |
| AC-2.8 | 排序白名单拒绝注入 |

---

## 6. P0-3 API 测试项目隔离

### 6.1 SPI

```java
public interface ProjectAccessValidator {
    void assertAccess(Long projectId);
    List<Long> accessibleProjectIds();
    Long currentTenantId();
}
```

- `PmProjectAccessValidator` 查询 `pm_project WHERE tenant_id=? AND del_flag=0`。
- `accessibleProjectIds()` 超过 900 个分块。
- 不提供生产 NoOp。

### 6.2 身份

- 删除 8 个 Controller 的 17 处 `@RequestParam Long userId`。
- 用 `CurrentUserAccessor.currentUserId()`；为空 11010。
- 写路径显式当前用户，不只依赖 MyBatis SYSTEM 回退。
- 旧客户端多传 userId 被忽略；文档标注废弃。

### 6.3 入口清单

| 模块 | 规则 |
|------|------|
| API 定义 | page（projectId 可空）；getDetail/get/update/delete/publish/deprecate/revert（按实体 projectId）；create（DTO.projectId 必填）；update 不允许改 projectId |
| API 分组 | create/update/delete/tree/**getDetail**；**getDetail 必须先加载分组 → assert 分组 projectId** |
| 集合 | page/detail/create/update/delete；detail 校验子资源 |
| 文件夹 | create/update/delete/tree；父集合 projectId |
| 条目 | add/batchAdd/update/delete/reorder；集合项目；`definition.projectId == collection.projectId` |
| 集合运行 | execute：集合项目 + `environment.projectId == collection.projectId`；写入 `run.projectId/environmentId`；runs/detail/delete 按 runId→collection→assert；存量 run 通过 collectionId 反查 |
| 环境 | page/list/detail/create/update/delete；projectId 必填/assert；`getVariableMap` assert `env.projectId`；null environmentId 空 Map 短路 |
| 调试执行 | projectId 必填；definitionId/environmentId 非空时 projectId 一致 |
| 调试历史 | page OR 条件用 `.and(w -> ...)` 包裹；detail/getByDefinitionId/delete/deleteBatch 按父资源 |
| cURL | 不纳入 |

### 6.4 列表规则

| 条件 | 规则 |
|------|------|
| projectId 非空 | 先 assert；按 projectId |
| projectId 为空 | `project_id IN (accessibleProjectIds())`；空集合空页 |
| currentTenantId null | 11010 |

- `CollectionController`、`ApiGroupController.tree` 等必填 projectId 改为 `required=false`。

### 6.5 前端

- 删除 `frontend/src/modules/api-test/**/api/*.ts` 的 userId 及所有调用方传参。
- 清理 `CollectionListPage.tsx`、`EnvironmentListPage.tsx`、`DefinitionDetailPage.tsx` 等的 userId 状态；`canMutate` 基于当前用户/权限。
- 更新 `createRequestInCollection.ts` 及其测试。
- `npm test` + `npm run build`。

### 6.6 文档

- `docs/backend/api_spec.md`：无版本头，先记 v0.1，本次 v0.2；删 userId、补 401/403。
- 新建 `docs/backend/api_spec.postman_collection.json`。
- 更新 `docs/product/04-api/modules/api-test.md`、`api-module-matrix.md`。

### 6.7 测试基础设施

- `BaseApiTest.TestConfig` 增加：
  - `TestProjectAccessValidator`：可配置可访问项目集合；不在集合抛 20002；`accessibleProjectIds` 返回集合。
  - `CurrentUserAccessor` 测试实现：固定 userId/tenantId/role，可切换。
  - `DenyAllProjectAccessValidator` 用于单点否定。
- `server/src/test/resources/application-test.yml`：SQLite 临时库、关闭真实 Keycloak issuer、测试 `JwtDecoder`/`AuthUserPrincipal`、两租户/两项目 fixture。
- MockMvc 放 `server`；Service 单测放 core。

### 6.8 验收

| ID | 标准 |
|----|------|
| AC-3.1 | 17 处 userId 全部移除 |
| AC-3.2 | 全部按 id 的读写越权 403（含 `GET /apitest/groups/{id}`） |
| AC-3.3 | 列表无 projectId 只返回可见项目；空集合空页 |
| AC-3.4 | 环境/definition/run/调试历史跨项目全部拒绝 |
| AC-3.5 | 前端不再发送 userId；测试与构建通过 |
| AC-3.6 | 文档 + Postman 同步 |

---

## 7. P0-4 容器租户隔离（一次修完）

### 7.1 根因

- `MonitorService` 用户路径已用 `getById(clusterId, tenantId)`；问题在 `MonitorController` 取租户可能为 null，且 `ClusterService.getById` 对 null 放行。
- `getByIdInternal` 只保留给启动自检。

### 7.2 HTTP fail closed

| 文件 | 改动 |
|------|------|
| `SecurityHelper` | 新增 `requireTenantId()`，缺失 11010 |
| `ClusterService.getById/page` | null→11010；不匹配→30005；删除 nullable 过滤 |
| `RegistryService.page/findById/create/listByTenant` | 全部 `requireTenantId()`；不匹配→30202 |
| `ImageSearchService/ImageSearchController` | `requireTenantId()` |
| `HelmChartService.tenantId()` | 删除 `PLATFORM_TENANT_ID=0` 回落 |
| `HelmReleaseService` | `requireTenantId()`；按 id 先 `getById(id, tenantId)` |
| 所有容器 Controller | `currentTenantId()` → `requireTenantId()` |
| `MonitorController` | `requireTenantId()`；删除 `getPrometheusUrlInternal` 死代码 |
| `ArgoWorkflowController` 全部入口 | 仅平台 admin（`CurrentUserAccessor.isAdmin()`，否则 11003/403）；按租户 namespace 隔离留待后续。前端必须同步三层隐藏，见 §7.4 |
| `getByIdInternal` | 改名 `getByIdForSystem`，移到 `ClusterSystemAccess`，仅启动自检 |

### 7.3 WebSocket

1. 前端 `PodLogPanel.tsx`、`PodShell.tsx` 追加 `tenantId=<localStorage TENANT_ID_KEY>`；localStorage 缺失时提示切换租户，不静默。
2. `ContainerWsAuthInterceptor`：
   - 注入 `KeycloakUserProvisioningService`，`ensureUser(...)` 得 `SysUser`（Long id + DB role）。
   - 检查 `SysUser.enabled == 1`；停用拒绝。
   - 租户：query `tenantId` → JWT `tenant_id` → 拒绝（不回落默认租户）。
   - `TenantAccessValidator.assertAccess`。
   - **从 path 解析 clusterId**，`clusterService.getById(clusterId, tenantId)`；失败握手阶段 403。
   - attributes 写 `Long userId`、`Long tenantId`。
3. **Handler 明确从 attributes 取 `Long tenantId`**（不再调用 `SecurityHelper.currentTenantId()` / `getByIdInternal`）：
   - `LogTailHandler`、`ResourceWatchHandler`、`PodShellWebSocketHandler`。
   - attributes 为空 → 关闭连接；否则 `getById(clusterId, tenantId)`。
4. 同步修改 LogTail/ResourceWatch 的 `(String)` 强转为 `(Long)`。
5. 平台 admin 用 DB role，与 HTTP 一致。

### 7.4 前端 Argo（三层隐藏）

1. **产品类型与切换器**：
   - `ConsoleProduct` 接口（`products.ts:9-17`）增加 `adminOnly?: boolean`；Argo 产品对象（`:95`）设 `adminOnly: true`。
   - `ProductSwitcher.tsx:25` 调 `groupProducts()` 时传入 `isAdmin`；`groupProducts` 过滤 `adminOnly` 产品。
2. **菜单项**：
   - `navigation.ts:204` 是 `argo:` 分组键，不是菜单项；在 208-214、215-221、222+ 的每个 `ProductMenuItem` 上设 `adminOnly: true`（`ProductMenuItem` 已有该字段）。
   - `menuForScope` 已按 `adminOnly` 过滤，确认生效。
3. **路由**：
   - `router.tsx` 当前没有 `/argo/**` 路由（落到 `:171` 的 `*` 兜底）；**新增** `argo/*` 路由，并在其外层加 admin 守卫（非 admin 重定向 403/首页）。
4. **工作台产品卡片**：`WorkbenchPage.tsx:10` 也调用 `groupProducts(...)`，必须传入 `isAdmin` 并过滤 `adminOnly`，否则非 admin 仍会看到 Argo 卡片。
5. **路由现状**：仓库当前没有 `/argo/*` 页面或路由（`router.tsx:171` 落到 `*` 兜底）。本轮不新增占位路由；若后续添加 Argo 页面，必须在外层挂 admin 守卫。AC-4.9 的“admin 正常”指后端接口 + 菜单可见，不要求新增页面。
6. 测试：非 admin 看不到产品切换器/菜单/工作台卡片；admin 正常。

### 7.5 测试改造

- `HelmChartServiceTest`、`HelmReleaseServiceTest` 等注入固定租户 `SecurityContext`，替换 `getById(..., isNull())`。
- 新增：`getById(id,null)`→11010；Registry/ImageSearch/HelmChart/HelmRelease 跨租户 403；Argo 非 admin 403；WS 握手 403（集群/停用/无租户）、正确租户/admin 正常；handler attributes 类型。

### 7.6 验收

| ID | 标准 |
|----|------|
| AC-4.1 | 租户 B 用租户 A clusterId 打开 WS 在握手阶段 403 |
| AC-4.2 | WS 无 query/claim → 拒绝；伪造租户 → 403 |
| AC-4.3 | 停用用户 WS/HTTP 均拒绝 |
| AC-4.4 | 正确租户 + admin 正常 |
| AC-4.5 | Registry/ImageSearch/HelmChart/HelmRelease/Monitor 跨租户或无租户 → 403/401 |
| AC-4.6 | `getById(id,null)` 自身抛 11010 |
| AC-4.7 | 启动自检仍遍历所有集群 |
| AC-4.8 | 前端 WS URL 带 tenantId 的测试通过 |
| AC-4.9 | Argo 非 admin 后端 403 + 前端菜单/路由隐藏 |

---

## 8. P0-5 迁移安全

### 8.1 05 零 UPDATE

#### 8.1.1 删除

- 删除 `05-pipeline-schema.sql:320` 的 DROP。
- 删除 11 条 UPDATE（`:190,342,346,357,389,422,442,457,492,504,515`）。
- 05 最终只含 `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `INSERT OR IGNORE`。

#### 8.1.2 种子并入 INSERT

| 数据 | 做法 |
|------|------|
| `pipeline_task_kind.command_template` | 在 `INSERT OR IGNORE INTO pipeline_task_kind` 列清单加入 `command_template`；把 8 条模板值写入对应 kind；**新增 IMAGE_COSIGN 种子行，`enabled=0`**（当前 05 无该行，原 UPDATE 是 no-op；IMAGE_COSIGN 只作为 IMAGE 的编译器子步骤，不进入任务市场/编辑器，前端 `JobKind` 与后端 `PipelineJobKind` 不新增该值） |
| `pipeline_task_kind.default_command`（DEPENDENCY_ANALYSIS） | 直接把去 `-q` 后的最终值写入 INSERT |
| `pipeline_task_kind_param.options_json`（DT_FAIL_ON） | 在 INSERT 列清单加入 `options_json`；DT_FAIL_ON 写完整 JSON，其它写 `'[]'` |
| `pipeline_job.command` 的 `-q` 清理 | 删除 UPDATE；历史 `pipeline_job` 不改写 |

结果：全新库种子完整；历史库 `INSERT OR IGNORE` 不改写任何已有行；重跑 05 零行更新。

#### 8.1.3 16-* 升级历史默认模板

新增 `deploy/charts/backend/files/db/16-upgrade-task-template-seeds.sql`：

- 只对“仍等于旧默认值”的行执行 UPDATE；用户编辑、空模板、自定义值不匹配即不动。
- 覆盖 `default_command`（精确匹配旧值）与 **`DT_FAIL_ON.options_json`（仅当值为 `'[]'`）**；**不覆盖 `command_template`**（空值视为用户主动选择 DEFAULT_TEMPLATE，无法与未初始化区分）；不处理 `pipeline_job.command`。
- **旧默认值清单**：实施前从 git 历史（`git log -p -- deploy/charts/backend/files/db/05-pipeline-schema.sql`）导出每条 UPDATE 的 old/new 对，写入 `16-*` 文件头注释与 `changes.md`；测试逐条使用这些字符串。
- 该文件包含 UPDATE，`check-migrations.sh` 对 16-* 使用显式白名单（仅此文件允许 UPDATE）。
- 单独评审、单独测试：旧默认值升级成功；用户编辑/空模板保留；重跑幂等。

#### 8.1.4 旧 schema 受控重建

**新增** `migrate.py` 函数 `ensure_dependency_component_schema(conn)`（当前 `migrate.py` 只有 `main()`）。

调用时机：迁移文件循环开始前执行一次；只在 `dependency_component` 存在且缺必需列时动作。

1. `PRAGMA table_info`；不存在或已含 `pipeline_id`、`identity_key`、`run_id`、`purl`、`name`、`version` → 返回。
2. **事务 1（独立提交）**：`DROP TABLE IF EXISTS dependency_component_legacy`；`CREATE TABLE dependency_component_legacy AS SELECT * FROM dependency_component;`；**COMMIT**。这样后续失败时 legacy 一定保留。
3. **事务 2（独立事务）**：`BEGIN IMMEDIATE` → `DROP TABLE dependency_component` → 删除 05 的 ledger 记录 → `COMMIT`；05 随后在自身事务中重建表。若 05 失败，legacy 备份保留，原表已不在，下次启动会再次执行 05 重建；legacy 始终可用于追溯。
4. 日志 warn；legacy 表固定名，避免累积。

embedded 同步：`SqliteSchemaInitializer.dropStaleDependencyComponentTable()` 改为同样的“先备份 legacy 再 DROP”，检查同样必需列。

### 8.2 双模式种子补齐

| 文件 | 改动 |
|------|------|
| `backend/server/src/main/resources/db/pipeline-schema.sql` | 删除 `:339`、`:343` 两条 UPDATE；把 command_template/options_json/default_command 写入 INSERT；**补 KUBECTL、6 个 DEPENDENCY_TRACK 参数、IMAGE_COSIGN 种子** |
| `SqliteSchemaInitializer.java` | 老 `dependency_component` 先备份 legacy 再 DROP；更新注释 |
| `deploy/.../05-pipeline-schema.sql` | 同步补齐 IMAGE_COSIGN，**`enabled=0`**；确保与 classpath 的公共 kind/参数一致 |
| 一致性测试 | 建 external/embedded 两库，比较 task kind 集合、6 个 DT 参数、command_template、IMAGE_COSIGN、索引；断言一致 |

### 8.3 迁移器

| 文件 | 改动 |
|------|------|
| `migrate.py` | 逐语句（`sqlite3.complete_statement`）；`isolation_level=None` + `BEGIN IMMEDIATE`；duplicate 仅 ALTER ADD COLUMN；其它错误中止且不写 checksum；成功后写 checksum |
| `migrate.py` | 统一 `_schema_migrations`；旧 `schema_migrations` 合并前验证（表/列/种子行/关键结果）；失败拒绝启动并给人工修复指引 |
| `migrate.py` | 硬保护：SQL 含 DROP/DELETE/TRUNCATE 拒绝（受控 Python 重建除外） |
| `migrate.py` | `busy_timeout` + 锁内二次 checksum |
| `migrate.sh` | `DB_PATH="$1" SQL_DIR="${2:-$(dirname "$0")}" exec python3 "$(dirname "$0")/migrate.py"` |
| checksum | Python/shell 统一按原始字节 |
| `scripts/migrations.lock` | 01-16 最终 SHA-256；CI 只读；`--write-lock` 仅本地 |
| `AGENTS.md` | 执行器、编号 16、07-09 跳号、05 零 UPDATE 约定 |

### 8.4 并发

- `deploy/charts/backend/values.yaml`：`strategy.type: Recreate`（SQLite RWO 单写）。
- `deploy/charts/backend/templates/configmap.yaml:14` 与 `templates/deployment.yaml:127-128` 的 `SPRING_DATASOURCE_URL` 增加 `?busy_timeout=5000`。
- `backend/server/src/main/resources/application.yml` 同步（embedded）。
- 双进程并发测试：一方成功，另一方等待/安全失败；`PRAGMA integrity_check` ok。

### 8.5 回滚

- 禁止 `helm rollback` 到包含旧 05 的版本（ConfigMap 会回退旧 `migrate.py` + 旧 05）。
- 升级前：`sqlite3 DB ".backup 'backup-<version>-<timestamp>.db'"`；`PRAGMA integrity_check`；记录路径。
- 回退：停止写入 → 恢复备份 → 回退镜像 → 再跑迁移检查。
- staging 演练一次；发布说明写明限制。
- 不声称有技术硬保护。

### 8.6 CI

- `scripts/check-migrations.sh`：
  - 禁止所有 SQL 出现 DROP/DELETE/TRUNCATE。
  - **除 `16-upgrade-task-template-seeds.sql` 外，全部迁移文件禁止 UPDATE**（不是只禁 01-06，07-15 与未来文件同样生效）。
  - 匹配规则：只匹配**语句开头**的关键字（去注释/空白后），避免把 `ON DELETE SET NULL`、`deleted`、`update_time` 等列名误报。
  - 校验 `scripts/migrations.lock`；编号唯一/排序。
- `pr-backend.yml` 与 push 触发的 `maven.yml` 都增加迁移目录 paths 与迁移 job，执行 `check-migrations.sh` + unittest；不能只在 PR 校验。
- `scripts/tests/test_migrate.py`：全新库种子完整（含 IMAGE_COSIGN/DT_FAIL_ON/command_template）、双次执行零改写、用户空模板/自定义值保留、16-* 升级匹配、dependency_component 哨兵、缺列旧表 legacy、duplicate、失败不写 checksum、旧记录合并、DROP/UPDATE 门禁、双进程、migrate.sh 任意目录、双模式一致。

### 8.7 文档

| 文档 | 改动 |
|------|------|
| `AGENTS.md` | 执行器、编号 16、05 零 UPDATE、16-* UPDATE 白名单 |
| `data-dictionary.md` §3.2 | `_schema_migrations`、旧表合并、legacy、16-* |
| `database-documentation-standard.md` §10 | 执行记录 |
| `tables/pipeline.md` | 种子、16-*、双模式 |
| `deployment-ops.md` | Recreate、busy_timeout、备份/恢复 runbook |
| `docs/review/...review...md` | 追加复核结论并升版本 |

### 8.8 验收

| ID | 标准 |
|----|------|
| AC-5.1 | 05 无 DROP/UPDATE/DELETE；仅 CREATE/INSERT |
| AC-5.2 | 重跑 05 后用户 command_template（含空值）、default_command、pipeline_job.command、dependency_component 全部不变 |
| AC-5.3 | 全新库种子完整：**19 行 kind（18 个 enabled + IMAGE_COSIGN enabled=0）**、DT_FAIL_ON options、8 个模板、DEPENDENCY_ANALYSIS 最终命令 |
| AC-5.4 | 16-* 只升级匹配旧默认值的行；用户编辑保留；重跑幂等 |
| AC-5.5 | 缺列旧表升级成功，legacy 保留数据；事务边界正确 |
| AC-5.6 | embedded/external 的 kind、DT 参数、模板、IMAGE_COSIGN 一致 |
| AC-5.7 | 部分失败不写 checksum；双记录表统一 |
| AC-5.8 | CI 门禁（含 UPDATE 拦截）与 unittest 在 PR 中执行 |
| AC-5.9 | 备份/恢复 runbook 在 staging 演练通过 |

---

## 9. P0-1 交付运维平台认证（本轮不做）

用户明确“不加”。本轮不改 `delivery-platform/`。

**残余风险**：后端无认证，raw manifest/chart 默认 NodePort。任何可达端口的人仍可导入 kubeconfig、部署/卸载产品、读写集群资源。

**临时缓解（不属本轮）**：网络限制 NodePort 来源；或临时 ClusterIP + port-forward。后续单独立项接入 Keycloak/共享 Token。

发布说明不得写成“已修复”。

---

## 10. 实施顺序与发布

| 阶段 | 内容 | 顺序 | 回滚 |
|------|------|------|------|
| 1 | P0-0 映射 | 后端 | 直接回退 |
| 2 | P0-5 迁移 + 05 + 16-* | 备份 DB；staging；生产 | 禁止 rollback 旧 05；恢复备份 |
| 3 | P0-2 PM | 后端 | 直接回退 |
| 4 | P0-3 API 测试 | 后端先、前端后 | 后端+前端一起回退 |
| 5 | P0-4 容器 | 前端先、后端后 | 后端+前端一起回退 |

- P0-5 升级前：`.backup` + `PRAGMA integrity_check`；记录备份路径。
- P0-5 后：检查 `_schema_migrations`、05/16 checksum、legacy 表、用户模板抽样。
- 每阶段独立 PR；不自动 commit/push。

## 11. 测试与验证

| 层 | 命令 |
|----|------|
| 后端单测 | `mvn -B -f backend/pom.xml -pl pm-core,api-test-core,container-core -am test` |
| 后端全量 | `mvn -B -f backend/pom.xml clean verify` |
| 前端 | `cd frontend && npm test && npm run build` |
| 迁移 | `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` |
| 门禁 | `bash scripts/check-migrations.sh` |
| Helm | `helm template deploy/charts/backend` 断言 Recreate + busy_timeout |
| 手工越权 | 两租户/两项目 fixture，逐 AC |

- `server` 测试 profile：`application-test.yml` + 测试 `JwtDecoder` + SQLite 临时库 + fixture。
- 容器 Helm 测试注入固定租户 `SecurityContext`。
- `test-cases.md` 标注 `文件::测试名`。

## 12. 文档与原型

### 12.1 接口文档（同目录同名）

| 变更 | Markdown | Postman |
|------|----------|---------|
| P0-3 删 userId | `docs/backend/api_spec.md`（v0.1→v0.2） | `docs/backend/api_spec.postman_collection.json` |
| P0-2 403/404 | `docs/pm/pm-api.md`（v1.3→v1.4，补变更记录） | `docs/pm/pm-api.postman_collection.json` |
| P0-4 容器 | `docs/container-platform/container-api.md`（新建，含 WS handshake 参数） | `docs/container-platform/container-api.postman_collection.json`（新建） |
| 说明 | container 视为**既有模块**，按 AGENTS「已有模块沿用现有位置」放 `docs/container-platform/`；Markdown 与 Postman 同目录同名。新模块才用 `docs/api/<module>/` | 同左 |
| P0-0 状态码 | `api-overview.md`、`error-code-design.md`（升版本+变更记录） | 各 collection 失败 example |

- 每个接口至少正常 + 参数校验失败 + 业务失败 + 鉴权失败 example。
- 产品文档 `docs/product/04-api/modules/container.md` 链接到 `container-api.md`，不复制两份。

### 12.2 前端原型

| 改动 | 判定 | 理由 |
|------|------|------|
| API 测试删 userId | 豁免 | 参数变化，无 UI/布局 |
| 容器 WS 加 tenantId | 豁免 | URL 参数变化 |
| canMutate 权限来源 | 豁免 | 无布局变化；若按钮可见性变化，补无权限态截图 |
| Argo 菜单/路由 adminOnly | 豁免 | 仅可见性/守卫变化，无新页面；在 changes.md 记录 |

### 12.3 Argo 前端

- `products.ts:95` Argo 产品 `adminOnly: true`；`ConsoleProduct` 增加该字段。
- `ProductSwitcher.tsx`、`WorkbenchPage.tsx:10` 的 `groupProducts(...)` 传入 `isAdmin` 并过滤。
- `navigation.ts` 的 Argo 菜单项（`:209/:216/:223`，不是 `:204` 的分组键）逐项 `adminOnly: true`。
- 当前仓库无 `/argo/*` 页面/路由，本轮不新增；后续新增时挂 admin 守卫。
- 测试：非 admin 产品切换器/菜单/工作台卡片均不显示；admin 正常。

## 13. 风险与开放决策

| 编号 | 风险/决策 | 处理 |
|------|-----------|------|
| R1 | 05 零 UPDATE，历史库不自动升级模板 | 16-* 精确匹配旧默认值；用户编辑保留 |
| R2 | Helm rollback 可能执行旧 DROP | 禁止 rollback + 备份 runbook + staging 演练 |
| R3 | API 测试 userId 删除 | 旧参数忽略；后端先发 |
| R4 | 多租户深链 | 显式 projectId 按项目租户；无 projectId 当前租户 |
| R5 | pm-core 零测试 | 引入 starter-test；单测 core，MockMvc server |
| R6 | WS query tenantId 可篡改 | 服务端成员 + cluster 校验 |
| R7 | api-test 测试 Bean | fixture 驱动 validator + accessor |
| R8 | admin 判定 | DB `sys_user.role` |
| R9 | 全局元数据类型 | 本轮 admin 门禁 |
| R10 | 交付平台认证未做 | §9；不得写成已修复 |
| R11 | 旧 external 缺列 | legacy 备份（事务 1）+ DROP/05（事务 2） |
| R12 | 双 Pod 并发 | Recreate + busy_timeout + 锁内 checksum |
| R13 | Argo 共享集群 | 本轮 admin-only + 前端隐藏；租户 namespace 后续 |
| R14 | 导入部分成功 | 项目无权整批拒绝；行级失败部分成功 |
| R15 | 全局字段 | 禁止新建；既有只读；写删 admin |
| R16 | 16-* 含 UPDATE | CI 白名单仅此文件；单独评审 |

## 14. 验收矩阵

| P0 | 核心 AC | 测试位置 |
|----|---------|----------|
| P0-0 | 401/403/404；filter 403；blob 错误 | `server` ExceptionAdviceTest/FilterTest；前端测试 |
| P0-2 | 全读/写/删除、update 旧项目、深链、排序、全局字段 | `pm-core` 单测 + `server` MockMvc |
| P0-3 | 17 处 userId、全入口 403、env/definition/run/history、测试 Bean | `api-test-core` 参数化 + `server` |
| P0-4 | WS 握手 403、handler attributes、Registry/ImageSearch/Helm/Monitor/Argo | `container-core` + `server` + 前端 |
| P0-5 | 05 零 UPDATE、16-*、数据保留、旧表、双模式、CI | `scripts/tests/test_migrate.py` + CI |
| 通用 | 文档/Postman/原型豁免/progress | 评审逐项 |

## 15. 文件清单（主要）

### 新增

- `backend/user-api/.../user/spi/ProjectAccessValidator.java`
- `backend/pm-core/.../project/service/PmProjectAccessValidator.java`
- `backend/container-core/.../service/cluster/ClusterSystemAccess.java`
- `backend/server/src/test/resources/application-test.yml`
- `backend/server/src/test/.../TestSecurityConfig.java`
- `deploy/charts/backend/files/db/16-upgrade-task-template-seeds.sql`
- `scripts/check-migrations.sh`
- `scripts/migrations.lock`
- `scripts/tests/test_migrate.py`
- `docs/backend/api_spec.postman_collection.json`
- `docs/pm/pm-api.postman_collection.json`
- `docs/container-platform/container-api.md`
- `docs/container-platform/container-api.postman_collection.json`

### 修改（主要）

- `backend/server/.../ApiErrorWriter.java`、`ExceptionAdvice.java`、`TenantContextFilter.java`
- `backend/user-core/.../DefaultTenantAccessValidator.java`
- `backend/pm-core/pom.xml`
- `backend/pm-core/.../ProjectService.java`、`WorkItemService.java`、`WorkItemCommentService.java`、`WorkItemActivityService.java`、`WorkItemImportExportService.java`、`QueryEngine.java`、`JsonSqlDialect.java`、`SavedViewService.java`、`ProjectModuleService.java`、`FieldDefinitionService.java`、`FieldLayoutService.java`、`StatusDefinitionService.java`、`IssueTypeSchemeService.java`、`ProjectIssueTypeService.java`
- `backend/server/.../SqliteSchemaInitializer.java`、`resources/db/pipeline-schema.sql`
- `backend/api-test-core/.../controller/*.java`、`service/*.java`、`BaseApiTest.java`
- `backend/container-core/.../ws/*.java`、`ClusterService.java`、`RegistryService.java`、`ImageSearchService.java`、`ImageSearchController.java`、`HelmChartService.java`、`HelmReleaseService.java`、`MonitorService.java`、`MonitorController.java`、`SecurityHelper.java`、`ArgoWorkflowController.java`、`ArgoWorkflowService.java`、相关测试
- `frontend/src/modules/api-test/**/api/*.ts` 及调用方、`PodLogPanel.tsx`、`PodShell.tsx`
- `frontend/src/shared/console/products.ts`、`navigation.ts`、`router.tsx`
- `deploy/charts/backend/files/db/05-pipeline-schema.sql`、`migrate.py`、`migrate.sh`
- `deploy/charts/backend/values.yaml`、`templates/configmap.yaml`、`templates/deployment.yaml`
- `backend/server/src/main/resources/application.yml`
- `.github/workflows/pr-backend.yml`
- `AGENTS.md` 与相关 API/数据库/部署文档

## 16. 评审问题闭环（v0.3 / v0.4）

| v0.3 问题 | v0.4 处理 |
|-----------|-----------|
| TenantContextFilter 不捕获 BizException | §4.1 同时捕获；validator 改 BizException |
| GET /pm/work-items/{id} 未守卫 | §5.3 明确加载 + assert |
| page 显式 projectId 未 assert | §5.3/§5.4 明确 WorkItemService.page 负责 |
| 字段 delete-by-id 未守卫 | §5.3 `deleteField` 加载 + assert |
| FieldLayout 跨项目回退 | §5.3 删除 `byType.size()==1` 回退，仅精确匹配 |
| project_id NULL 全局字段 | §5.5 禁止新建；既有只读；写删 admin |
| Argo admin 与 UI 不一致 | §7.2/§7.4/§12.3 后端 + 前端菜单/路由 |
| WS handler 租户来源 | §7.3 明确从 attributes 取 Long，三处 handler |
| PM §5.3/§5.5 矛盾 | §5.3/§5.5 统一“禁止改归属” |
| IMAGE_COSIGN 无 INSERT | §8.1.2 新增种子行 |
| embedded/external 一致 | §8.2 补 KUBECTL/DT 参数/IMAGE_COSIGN；AC-5.6 |
| busy_timeout 未接线 | §8.4 configmap/deployment URL |
| container 文档不同目录 | §12.1 统一 `docs/container-platform/` |
| legacy 事务矛盾 | §8.1.4 事务 1 备份 COMMIT，事务 2 DROP/05 |
| CI 不拦 UPDATE | §8.6 01-06 禁 UPDATE；仅 16-* 白名单 |
| 05 零 UPDATE 用户确认 | §2.1 用户已选；16-* 升级 |
| 双模式差异 | §2.1 用户已选本轮补齐 |
| FieldLayout 回退只写在 §16 | §5.2 正文明确删除 `byType.size()==1` 回退 |
| Argo 前端三层隐藏 | §7.4 增加 `ConsoleProduct.adminOnly`、ProductSwitcher/groupProducts 过滤、逐菜单项、新增 `argo/*` 路由守卫 |
| legacy 事务 2 矛盾/函数不存在 | §8.1.4 明确新增函数、事务 1 独立提交、事务 2 失败 ROLLBACK 恢复原表 |
| IMAGE_COSIGN enabled 语义 | §8.1.2 `enabled=0`，不进入任务市场/前端 JobKind |
| AC-5.3 kind 计数 | §8.8 改为 19 行 / 18 enabled |
| blob 错误修复位置 | §4.1 改为错误拦截器 `:47-61` async + blob.text |
| 11006/12012 映射 | §4.2 增加 400 |
| 16-* 旧值清单/DT_FAIL_ON | §8.1.3 引用 git 历史、纳入 DT_FAIL_ON options_json |
| CI UPDATE 范围/push | §8.6 除 16-* 外全禁；maven.yml 同步 |
| ApiGroup.getDetail 守卫规则 | §6.3 明确先加载分组再 assert |
