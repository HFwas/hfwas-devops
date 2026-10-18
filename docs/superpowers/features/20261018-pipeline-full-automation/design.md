# 流水线全量自动化 — 功能设计

> 日期：2026-10-18
> 版本：v0.1
> 状态：已确认
> 所属模块：pipeline
> 数据来源：`docs/product/05-testing/cases/pipeline.md`、`backend/pipeline-core/src/test/`、`frontend/src/modules/pipeline/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：自动化范围、分层策略、mock 方案、缺陷处理与验收标准 |

---

## 1. 背景与目标

流水线模块已有 157 条正式测试用例，但自动化覆盖集中在少数纯逻辑与扫描服务：

- 后端 14 个测试类、约 100 个 `@Test`，全部为 JUnit 5 + Mockito，无 Spring 上下文；
- `PipelineDefinitionService`、`PipelineCredentialService`、`PipelineRunArtifactService`、`PipelineJobParamService`、全部 Controller、WebSocket 均无测试；
- 前端 15 个测试文件、46 个 `it`，凭证页、取消/审批 mutation、任务类型启停/版本、删除流水线、编辑器页面级校验未覆盖；
- CI 的后端 job 使用 `-Dmaven.test.skip=true`，测试不在流水线中执行。

目标：把 157 条用例中**可确定性模拟**的部分自动化，形成可在本地与后续 CI 中稳定执行的回归套件；真实 Tekton/K8s 集群与浏览器 E2E 明确保留手工，并在用例文档标注原因。

## 2. 范围与非目标

### 2.1 范围

| 层级 | 内容 |
|------|------|
| 后端 Service | 定义、凭证、运行/队列、任务参数、产物、任务类型、依赖扫描/组件 |
| 后端 Controller | 8 个 `standaloneSetup` MockMvc 测试类，验证路由、参数绑定、响应包装、错误码 |
| 后端 WebSocket | 握手 JWT 提取、Handler 鉴权/归属/路径/Pod 未分配等可测分支 |
| 前端 | 凭证两页、运行详情 mutation、任务市场交互、编辑器校验、列表/详情状态 |

### 2.2 非目标

- 真实 Tekton 轮询、step 执行、日志汇聚/截断、Pod 信息回写（依赖真实集群）；
- 真实 Pod 终端的 exec/ephemeral/debug Pod 全链路、30s/90s 心跳、resize 实时行为；
- 浏览器原生 `beforeunload` 确认框、逐像素视觉回归；
- 修改生产代码行为、修复 PIPE-9xx 缺陷、引入 Testcontainers/fabric8 mock/Testing Library；
- 修改 CI 的 `-Dmaven.test.skip=true`（后续单独变更）。

## 3. 现状基线

| 项目 | 基线 |
|------|------|
| 后端测试框架 | `spring-boot-starter-test`（JUnit 5、Mockito、AssertJ、MockMvc） |
| 后端现有测试 | pipeline-core 14 个类、约 100 个 `@Test`，纯 Mockito |
| 可复用模式 | `PipelineRunServiceTest` 的手工构造、`container-core` 的 `standaloneSetup` |
| 前端测试框架 | Vitest 5 + happy-dom；无 Testing Library/msw |
| 前端现有测试 | 15 个文件、46 个 `it`；`createRoot + act` + 局部 helper |
| CI | 后端跳过测试；前端 PR 跑 `npm run build` + `npm test` |

## 4. 分层策略

### 4.1 后端 Service

纯 Mockito 单测，不启动 Spring：

- `@ExtendWith(MockitoExtension.class)` + `@Mock` Mapper/Service/Accessor/Executor；
- 被测对象在 `@BeforeEach` 或方法内 `new`；
- Mapper 分页用 `Page` 返回值；插入用 `thenAnswer` 回填 id；
- 断言优先断言“返回结果/异常 code 与 message/关键 Mapper 调用”，不断言 MyBatis 生成的完整 SQL。

### 4.2 后端 Controller

`MockMvcBuilders.standaloneSetup(new XxxController(mock...))`：

- 不加载 `SecurityConfig`，因此不覆盖真实 JWT 鉴权；
- 统一挂 `PipelineTestExceptionAdvice`，复刻 `BizException` → HTTP 200 + `BaseResult.code`；
- 断言 `status().isOk()`、`$.code`、`$.data` 与关键字段。

### 4.3 WebSocket

- 握手测试：mock `JwtDecoder`，构造 `MockServerHttpRequest/Response`，验证子协议中的 JWT 提取、缺失/无效拒绝、token 回写；
- Handler 测试：同包测试以访问 protected `handleTextMessage`；mock `WebSocketSession`、`ObjectProvider<KubernetesClient>`、Mapper；
- 构造器启动的 daemon scheduler 在 `@AfterEach` 通过反射 `shutdownNow()`，不等待真实心跳。

### 4.4 前端

沿用现有风格：`vi.mock('@/modules/pipeline/api/pipeline')` + `QueryClientProvider` + `MemoryRouter` + `createRoot/act`：

- 新增 `frontend/src/test-utils/pipeline.tsx` 仅供新测试使用；
- 不重构现有 6 个页面测试的局部 helper；
- 不新增 `setupFiles`；每个新文件 `beforeEach` 调用 `prepareActEnvironment()`。

## 5. 测试数据与 Mock 策略

| 依赖 | 策略 |
|------|------|
| `CurrentUserAccessor` | 默认 `userId=7`、`tenantId=9`、`displayName="ada"`；跨租户用例返回/构造 `tenantId=8` |
| Mapper | Mockito mock；分页返回 `Page`；插入回填 id；`ArgumentCaptor` 断言租户与排序 |
| `PipelineExecutor` | mock `isReady/submit/cancel`；无事务时 `submitAfterCommit` 同步执行 |
| `KubernetesClient` | `RETURNS_DEEP_STUBS` 或分层 mock；集中在 `support/K8sFixtures` |
| `ObjectProvider` | `getIfAvailable()` 返回 null（未配置集群）或 mock client |
| 文件系统 | `@TempDir` + `ReflectionTestUtils` 设置 `storageBase` |
| HTTP | 进程内 `HttpServer`（端口 0）提供成功/非 2xx；`@AfterEach` 关闭 |
| 定时线程 | 反射获取 scheduler 并 `shutdownNow()`；不等待真实时间 |
| 时间 | 只断言非空/单调/顺序，不断言精确时间值 |

## 6. 已知缺陷处理

不修生产代码。对已记录的 PIPE-9xx 差异，测试写成期望行为并标注 `@Disabled("PIPE-9xx: ...")`：

| 编号 | 处理 |
|------|------|
| PIPE-903 | 取消后 job 应为 CANCELLED（Disabled） |
| PIPE-908 | 批量扫描 `scans=null` 应返回参数错误（Disabled） |
| PIPE-909 | 扫描任务列表应返回最近任务（Disabled） |
| PIPE-910 | api_select 前端应渲染下拉（前端 `it.skip`） |
| PIPE-915 | fixed 参数不可被运行时覆盖（Disabled） |
| PIPE-916 | 产物归属校验（Disabled 占位测试） |
| PIPE-917 | 产物文件名清洗（Disabled） |

其余差异（PIPE-901/902/905/906/907/911/912/913/914）在 `test-cases.md` 标为“待确认/手工”，不写通过式断言。其中 PIPE-911（凭证引用统计跨租户）业务语义待确认，标为手工。

## 7. 命名与目录

- 后端测试类：`<ClassName>Test`，方法名使用行为描述（如 `createWithoutCloneAllowsEmptyRepoUrl`）；
- 测试支持类：`support/PipelineFixtures`、`support/K8sFixtures`、`controller/PipelineTestExceptionAdvice`；
- 前端：`CredentialPages.test.tsx`、`frontend/src/test-utils/pipeline.tsx`；
- 每条测试注释或 `@DisplayName` 引用 PIPE 编号，便于映射。

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-AUTO-01 | `mvn -pl backend/pipeline-core -am test` 全绿 |
| AC-AUTO-02 | `mvn -pl backend/server test` 全绿（`-am` 会执行无关模块的既有失败用例） |
| AC-AUTO-03 | `cd frontend && npm test` 全绿 |
| AC-AUTO-04 | 157 条 PIPE 用例在 `test-cases.md` 中均有“自动化/手工/Disabled”结论 |
| AC-AUTO-05 | `@Disabled`/`it.skip` 数量与已知差异清单一致，无静默跳过 |
| AC-AUTO-06 | 无生产代码改动（`backend/**/src/main`、`frontend/src` 业务代码） |
| AC-AUTO-07 | 独立 agent 校验文档 ↔ 测试 ↔ 执行结果一致 |

## 9. 风险与边界

| 风险 | 措施 |
|------|------|
| deep stub 脆弱 | 集中在 fixture；失败改分层 mock，不放宽断言 |
| MockMvc 不覆盖真实安全 | 在文档中注明；鉴权由后续集成测试/手工覆盖 |
| `bash -n` 平台依赖 | `Assumptions.assumeTrue` 跳过无 bash 环境 |
| 前端页面测试依赖文案 | 断言使用稳定标签/aria，避免易变文案 |
| 测试耗时 | 纯单测为主；每个模块无新增 Spring 上下文 |
| 覆盖率虚报 | 手工/Disabled 用例单列，不计入自动化通过数 |
