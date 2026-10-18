# 流水线全量自动化 — 代码变更文档

> 日期：2026-10-18
> 版本：v0.5
> 状态：已实施，独立校验通过
> 所属模块：pipeline
> 数据来源：`docs/product/05-testing/cases/pipeline.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：计划新增/修改文件、与 PIPE 用例的对应、验证方式 |
| v0.2 | 2026-10-18 | 实施完成：补齐后端 Service/Controller/WebSocket 与前端页面测试；回填测试计数与初步验证结果 |
| v0.3 | 2026-10-18 | 根据第一轮独立校验修正统计口径、Disabled 清单、PIPE-910/915/916 占位测试与文档映射 |
| v0.4 | 2026-10-18 | 第二轮校验：修正混合统计为 4、PIPE-111 标为手工；回填最终校验结论 |
| v0.5 | 2026-10-18 | 精确统计为 127 纯自动化 / 4 部分 / 4 Disabled / 22 手工（含 PIPE-111 仅记录缺陷） |

---

## 1. 变更原则

- 只新增/修改测试与文档，**未修改生产代码行为**：
  - `backend/**/src/main` 无改动；
  - `frontend/src` 下仅新增 `test-utils`、`*.test.ts(x)`，无业务代码改动。
- 已知缺陷用 `@Disabled`/`it.skip` 记录期望行为，不修业务代码。
- 未 commit / 未 push；未修改 CI 配置。

## 2. 实际改动文件

### 2.1 Feature 文档

| 文件 | 说明 |
|------|------|
| `docs/superpowers/features/20261018-pipeline-full-automation/design.md` | 功能设计 |
| `.../test-cases.md` | PIPE-001..157 自动化映射（127 纯自动化 / 4 部分 / 4 Disabled / 22 手工） |
| `.../changes.md` | 本文档 |

### 2.2 后端测试支持

| 文件 | 说明 |
|------|------|
| `backend/pipeline-core/src/test/java/.../controller/PipelineTestExceptionAdvice.java` | 业务异常 → HTTP 200 + BaseResult.code |

### 2.3 后端 Service 测试

| 文件 | 类型 | 测试数（含既有） | 对应 PIPE |
|------|------|------------------|-----------|
| `service/PipelineDefinitionServiceTest.java` | 新增 | 25 | 001–020、151 |
| `service/PipelineCredentialServiceTest.java` | 新增 | 16 | 063–076、153–156 |
| `service/PipelineRunServiceTest.java` | 扩展 | 15（+10，1 Disabled） | 021–033、036、040、047–050、152 |
| `service/PipelineRunQueueServiceTest.java` | 新增 | 10（2 Disabled） | 041–046 |
| `service/PipelineJobParamServiceTest.java` | 新增 | 12 | 027、032、033、050、089、090、915 |
| `service/PipelineRunArtifactServiceTest.java` | 新增 | 13（2 Disabled） | 103–111、157 |
| `service/PipelineTaskKindServiceTest.java` | 扩展 | 19（+15） | 077–102、155 |
| `service/DependencyScanServiceTest.java` | 扩展 | 28（+3，2 Disabled） | 112–120 |
| `service/DependencyComponentServiceTest.java` | 修正 | 4 | 121、122 |

### 2.4 后端 Controller 测试

| 文件 | 测试数 | 对应 PIPE |
|------|--------|-----------|
| `controller/PipelineControllerTest.java` | 12 | 001、003、016、019–021、039–042、048–050 |
| `controller/PipelineCredentialControllerTest.java` | 8 | 063–076、153、156 |
| `controller/PipelineTaskKindControllerTest.java` | 11 | 077–102 |
| `controller/PipelineRunArtifactControllerTest.java` | 6 | 103–111 |
| `controller/PipelineJobParamControllerTest.java` | 7 | 027、032、033、050 |
| `controller/DependencyScanControllerTest.java` | 3 | 112、118–120 |
| `controller/DependencyComponentControllerTest.java` | 2 | 121、122 |
| `controller/PodExecControllerTest.java` | 5 | 123–126 |

### 2.5 WebSocket 测试

| 文件 | 测试数 | 对应 PIPE |
|------|--------|-----------|
| `backend/server/src/test/java/com/hfwas/devops/ws/PodExecAuthHandshakeInterceptorTest.java` | 4 | 127、128 |
| `.../PodExecWebSocketHandlerTest.java` | 8 | 124–126 |

### 2.6 前端测试

| 文件 | 类型 | 新增测试 |
|------|------|----------|
| `frontend/src/test-utils/pipeline.tsx` | 新增 | 测试支撑 |
| `frontend/src/modules/pipeline/pages/CredentialPages.test.tsx` | 新增 | 9 |
| `.../PipelineRunDetailPage.test.tsx` | 扩展 | +3 |
| `.../TaskKindPages.test.tsx` | 扩展 | +7 |
| `.../PipelineEditorPage.test.tsx` | 扩展 | +3 |
| `.../PipelineListPage.test.tsx` | 扩展 | +3 |
| `.../PipelineDetailPage.test.tsx` | 扩展 | +1 |
| `.../runParams.test.ts` | 扩展 | +5 |

流水线前端测试合计 77 条（既有 46 + 新增 31）。

### 2.7 产品文档

| 文件 | 变更 |
|------|------|
| `docs/product/05-testing/cases/pipeline.md` | v0.3：回填 157 条用例的自动化 / 手工 / Disabled 落点 |

## 3. 计划外偏差

| 日期 | 偏差 | 原因 |
|------|------|------|
| 2026-10-18 | 额外修正 `DependencyComponentServiceTest` 的 2 条既有失败用例 | 该测试 mock 的 `selectList` 未模拟 SQL 过滤，原断言与实现职责不符；改为返回过滤后结果并注明 SQL 过滤由 mapper 负责 |
| 2026-10-18 | 未单独测试资源量格式非法（PIPE-085） | fabric8 `Quantity` 在 surefire 环境中解析行为与 jshell 不一致，避免 flaky，标为手工 |
| 2026-10-18 | 未使用计划中的 `support/PipelineFixtures`、`K8sFixtures` | 各测试类局部 fixture 更直观，避免过度抽象；仅保留 Controller 测试 advice |

## 4. 实施记录

| 项目 | 结果 |
|------|------|
| 新增后端测试方法数 | 约 170（pipeline-core 258 条、server 12 条） |
| 新增前端测试数 | 31（流水线模块合计 77） |
| `@Disabled` 数 | 7（Queue 2、Artifact 2、DependencyScan 2、RunService 1）；前端 `it.skip` 1 |
| 生产代码改动 | 无 |

## 5. 校验结果

> 由独立 agent 在实施完成后填写：文档一致性、执行命令、通过/失败/跳过数、差异清单。

| 项目 | 结果 |
|------|------|
| 文档一致性 | 通过（第二轮校验，157 行唯一，统计 127/4/4/22） |
| `mvn -pl backend/pipeline-core -am test` | 258 tests / 0 failures / 7 skipped |
| `mvn -pl backend/server clean test` | 12 WS tests / 0 failures |
| `cd frontend && npm test` | 219 passed + 1 skipped / 62 files |
| PIPE 映射完整性 | 157/157 已映射（初步） |
| 第一轮独立校验 | 不通过：统计口径、Disabled 清单、PIPE-910/915/916 占位测试、映射与产品文档行损坏；已全部修正 |
| 第二轮独立校验 | 通过：测试全绿；剩余统计/111 行问题已修正并复核 |
