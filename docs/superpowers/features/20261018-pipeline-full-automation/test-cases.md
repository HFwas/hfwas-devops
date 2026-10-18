# 流水线全量自动化 — 测试用例映射

> 日期：2026-10-18
> 版本：v0.1
> 状态：已完成（映射 157 条正式用例）
> 所属模块：pipeline
> 数据来源：`docs/product/05-testing/cases/pipeline.md`、`backend/pipeline-core/src/test`、`backend/server/src/test`、`frontend/src/modules/pipeline`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：PIPE-001..157 的自动化 / Disabled / 手工映射 |

---

## 1. 统计

| 状态 | 数量 | 说明 |
|------|------|------|
| 纯自动化 | 127 | 单测/MockMvc/组件测试可稳定执行 |
| 自动化 + 手工/Disabled 混合 | 4 | 可模拟部分已自动化，剩余见备注 |
| Disabled | 4 | 正式用例中有 `@Disabled` 测试（043/046/119/157；910/915/916 为 9xx 缺陷记录） |
| 手工 | 22 | 21 条手工 + PIPE-111 仅记录缺陷（期望行为见 PIPE-916） |
| **合计** | **157** | |

自动化落点：后端 `backend/pipeline-core/src/test`（Service/Controller）、`backend/server/src/test`（WebSocket）；前端 `frontend/src/modules/pipeline/**/*.test.tsx`。

## 2. 用例映射

| 编号 | 优先级 | 方式 | 测试位置 / 原因 | 备注 |
|------|--------|------|-----------------|------|
| PIPE-001 | P0 | 自动化 | PipelineDefinitionServiceTest::createPersistsPipelineStagesAndJobs; PipelineControllerTest::createReturnsId |  |
| PIPE-002 | P0 | 自动化 | PipelineDefinitionServiceTest::createPersistsPipelineStagesAndJobs; PipelineControllerTest::createReturnsId |  |
| PIPE-003 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsBlankName; PipelineControllerTest::createRejectsBlankName |  |
| PIPE-004 | P1 | 自动化 | PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl |  |
| PIPE-005 | P1 | 自动化 | GitRemoteTest::*（既有） |  |
| PIPE-006 | P2 | 自动化 | PipelineDefinitionServiceTest::createWithoutCloneAllowsEmptyRepoUrl |  |
| PIPE-007 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsUnknownKind |  |
| PIPE-008 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsEmptyStages |  |
| PIPE-009 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsStageWithoutJobs |  |
| PIPE-010 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsBlankStageName; ::createRejectsBlankJobName |  |
| PIPE-011 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsSecondClone |  |
| PIPE-012 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsApprovalSharingStage |  |
| PIPE-013 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsParallelImage |  |
| PIPE-014 | P1 | 自动化 | PipelineDefinitionServiceTest::createRejectsMissingCommand |  |
| PIPE-015 | P2 | 自动化 | PipelineGraphValidatorTest::allowsShellOnlyWithoutClone（既有） |  |
| PIPE-016 | P1 | 自动化 | PipelineDefinitionServiceTest::updateDeletesChildrenBeforePersist; PipelineControllerTest::updatePassesPathId |  |
| PIPE-017 | P2 | 自动化 | PipelineDefinitionServiceTest::pageSizeIsCapped; ::pageUsesTenantDefaultsAndKeyword |  |
| PIPE-018 | P1 | 自动化 | PipelineDefinitionServiceTest::pageSizeIsCapped; ::pageUsesTenantDefaultsAndKeyword |  |
| PIPE-019 | P1 | 自动化 | PipelineDefinitionServiceTest::deleteRemovesChildrenAndPipeline; PipelineControllerTest::deleteReturnsOk |  |
| PIPE-020 | P1 | 自动化 | PipelineDefinitionServiceTest::deleteRejectsOtherTenant; PipelineControllerTest::deleteMissingReturnsNotFound |  |
| PIPE-021 | P0 | 自动化 | PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady |  |
| PIPE-022 | P0 | 自动化 | runParams.test::PIPE-022; PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady |  |
| PIPE-023 | P1 | 自动化 | PipelineRunServiceTest::startRejectsMissingRequiredRuntimeParam; runParams.test::PIPE-023/024 |  |
| PIPE-024 | P2 | 自动化 | PipelineRunServiceTest::startRejectsMissingRequiredRuntimeParam; runParams.test::PIPE-023/024 |  |
| PIPE-025 | P1 | 自动化 | PipelineRunServiceTest::startRejectsSelectValueOutsideOptions |  |
| PIPE-026 | P1 | 自动化 | PipelineRunServiceTest::startRejectsSelectValueOutsideOptions |  |
| PIPE-027 | P1 | 自动化 + Disabled | PipelineJobParamServiceTest::createKeepsFixedMode；PIPE-915 Disabled |  |
| PIPE-028 | P1 | 自动化 | PipelineRunServiceTest::hiddenParamsStayOutOfRunDialogAndKeepMarketplaceDefault |  |
| PIPE-029 | P1 | 自动化 | PipelineRunServiceTest::startUsesGitRefOverrideAndReplacesCmdPlaceholder |  |
| PIPE-030 | P1 | 自动化 | PipelineRunServiceTest::startUsesGitRefOverrideAndReplacesCmdPlaceholder |  |
| PIPE-031 | P2 | 自动化 | TektonCompilerTest::commandPlaceholderNotBashVariable（既有） |  |
| PIPE-032 | P2 | 自动化 | PipelineJobParamServiceTest::createDefaultsAndBackfillsId / getParamsGroupedByJob; PipelineJobParamControllerTest::* |  |
| PIPE-033 | P2 | 自动化 | PipelineJobParamServiceTest::createDefaultsAndBackfillsId / getParamsGroupedByJob; PipelineJobParamControllerTest::* |  |
| PIPE-034 | P0 | 自动化 | PipelineRunServiceTest::startWithoutClusterFailsWithReadableMessage |  |
| PIPE-035 | P1 | 自动化 | PipelineRunServiceTest::startWaitsWhenFirstStageIsApproval |  |
| PIPE-036 | P0 | 自动化 | PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady |  |
| PIPE-037 | P0 | 自动化 | PipelineRunServiceTest::approveSubmitsSegmentAfterLeadingGate |  |
| PIPE-038 | P1 | 自动化 | PipelineRunServiceTest::approveConsecutiveGatesStayWaiting |  |
| PIPE-039 | P1 | 自动化 | PipelineRunServiceTest::approveWhenNotWaitingRejected; PipelineControllerTest::approveRejectedReturnsBizCode |  |
| PIPE-040 | P1 | 自动化 | PipelineRunServiceTest::approveLastGateCompletesRun |  |
| PIPE-041 | P0 | 自动化 | PipelineRunQueueServiceTest::cancelRunningMarksCancelledAndCallsExecutor; PipelineControllerTest::cancelReturnsRun |  |
| PIPE-042 | P2 | 自动化 | PipelineRunQueueServiceTest::cancelTerminalIsNoop |  |
| PIPE-043 | P1 | Disabled | PipelineRunQueueServiceTest::cancelShouldMarkJobsCancelled（PIPE-903） |  |
| PIPE-044 | P1 | 自动化 | PipelineRunQueueServiceTest::startKeepsQueuedWhenConcurrencyLimitReached |  |
| PIPE-045 | P1 | 自动化 | PipelineRunQueueServiceTest::dequeueSubmitsOldestQueuedRun / dequeueWithoutQueuedRunIsNoop / dequeueSkipsEmptyGraph |  |
| PIPE-046 | P2 | Disabled | PipelineRunQueueServiceTest::approveShouldRespectConcurrencyLimit（PIPE-904） |  |
| PIPE-047 | P2 | 自动化 | PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady（trigger/triggeredBy） |  |
| PIPE-048 | P1 | 自动化 | PipelineRunServiceTest::getRejectsRunFromAnotherPipeline; PipelineControllerTest::getRunMissingReturnsNotFound |  |
| PIPE-049 | P2 | 自动化 | PipelineRunServiceTest::pageRunsReturnsRowsWithoutJobs |  |
| PIPE-050 | P1 | 自动化 | PipelineRunServiceTest::hiddenParams...; PipelineJobParamServiceTest::getDefaultParams*; PipelineControllerTest::defaultParamsReturnsList |  |
| PIPE-051 | P0 | 手工 | 真实 Tekton 执行/轮询；无集群路径已由自动化覆盖 | 需本地 k3s/Tekton |
| PIPE-052 | P0 | 手工 | 真实 Tekton 执行/轮询；无集群路径已由自动化覆盖 | 需本地 k3s/Tekton |
| PIPE-053 | P0 | 手工 | 真实 Tekton 执行/轮询；无集群路径已由自动化覆盖 | 需本地 k3s/Tekton |
| PIPE-054 | P1 | 手工 | 真实 Tekton 执行/轮询；无集群路径已由自动化覆盖 | 需本地 k3s/Tekton |
| PIPE-055 | P1 | 手工 | 真实 Tekton 执行/轮询；无集群路径已由自动化覆盖 | 需本地 k3s/Tekton |
| PIPE-056 | P0 | 自动化 | TektonCompilerTest::masksSecretInLogs（既有） |  |
| PIPE-057 | P2 | 手工 | 真实集群日志汇聚/截断与 Pod 回写 | 需本地集群 |
| PIPE-058 | P2 | 手工 | 真实集群日志汇聚/截断与 Pod 回写 | 需本地集群 |
| PIPE-059 | P1 | 自动化 | DnsNamesTest::parsesCommitMarkerFromCloneLog（既有） |  |
| PIPE-060 | P2 | 自动化 | TektonPipelineExecutorTest::stepDiagnosticsIncludesImagePullWaiting（既有） |  |
| PIPE-061 | P2 | 自动化 | TektonPipelineExecutorTest::jobTimesUseTektonStepTimestamps 等（既有） |  |
| PIPE-062 | P3 | 手工 | 前端每秒耗时刷新，需 E2E/视觉验证 |  |
| PIPE-063 | P0 | 自动化 | PipelineCredentialServiceTest::createPasswordEncryptsSecret; PipelineCredentialControllerTest::saveReturnsId |  |
| PIPE-064 | P1 | 自动化 | PipelineCredentialServiceTest::createTokenDefaultsUsername |  |
| PIPE-065 | P0 | 自动化 | PipelineCredentialServiceTest::createKubeconfigWithoutUsername |  |
| PIPE-066 | P1 | 自动化 | PipelineCredentialServiceTest::createRejectsUnsupportedKind; PipelineCredentialControllerTest::saveRejectsUnsupportedKind |  |
| PIPE-067 | P1 | 自动化 | PipelineCredentialServiceTest::createPasswordRequiresUsername |  |
| PIPE-068 | P1 | 自动化 | PipelineCredentialServiceTest::createRequiresSecret |  |
| PIPE-069 | P1 | 自动化 | PipelineCredentialServiceTest::updateWithBlankSecretKeepsCiphertext |  |
| PIPE-070 | P0 | 自动化 | PipelineCredentialServiceTest::updateWithSecretReencrypts |  |
| PIPE-071 | P0 | 自动化 | PipelineCredentialServiceTest::getAndDecryptNeverExposeSecretInVo; PipelineCredentialControllerTest::listReturnsCredentials |  |
| PIPE-072 | P2 | 手工 | 当前允许重名（DB 无唯一约束），待产品确认 |  |
| PIPE-073 | P0 | 自动化 | PipelineCredentialServiceTest::deleteRejectsReferencedCredential; Controller deleteReferencedReturnsBizCode |  |
| PIPE-074 | P1 | 自动化 | PipelineCredentialServiceTest::deleteRemovesUnreferencedCredential; Controller deleteReturnsOk |  |
| PIPE-075 | P1 | 自动化 | PipelineCredentialServiceTest::getRejectsOtherTenant; Controller getOtherTenantReturnsNotFound |  |
| PIPE-076 | P2 | 自动化 | PipelineCredentialServiceTest::pageUsesDefaults; Controller pageReturnsPage |  |
| PIPE-077 | P0 | 自动化 | PipelineTaskKindControllerTest::listReturnsKinds; PipelineTaskKindServiceTest::listAllIncludesDisabledKinds |  |
| PIPE-078 | P1 | 自动化 | PipelineTaskKindControllerTest::getMissingReturnsNotFound |  |
| PIPE-079 | P0 | 自动化 | PipelineTaskKindServiceTest::saveDoesNotChangeVersion; Controller updateReturnsOk |  |
| PIPE-080 | P1 | 自动化 | PipelineTaskKindControllerTest::updateRejectsBlankLabel（@Valid） |  |
| PIPE-081 | P1 | 自动化 | PipelineTaskKindServiceTest::updateRejectsBlankTaskGroup |  |
| PIPE-082 | P2 | 自动化 | PipelineTaskKindServiceTest::updateKeepsToolImageWhenDtoFieldIsNull |  |
| PIPE-083 | P1 | 自动化 | PipelineTaskKindServiceTest::updateRejectsCpuOverPlatformLimit / updateRejectsMemoryOverPlatformLimit |  |
| PIPE-084 | P1 | 自动化 | PipelineTaskKindServiceTest::updateRejectsCpuOverPlatformLimit / updateRejectsMemoryOverPlatformLimit |  |
| PIPE-085 | P2 | 手工 | fabric8 Quantity 解析行为随环境，未自动化 |  |
| PIPE-086 | P1 | 自动化 | PipelineTaskKindServiceTest::updateRejectsInvalidParamKey |  |
| PIPE-087 | P1 | 自动化 | PipelineTaskKindServiceTest::updateReplacesParamsAndRejectsDuplicateKey |  |
| PIPE-088 | P1 | 自动化 | PipelineTaskKindServiceTest::updateRejectsUnsupportedParamType |  |
| PIPE-089 | P1 | 自动化 | PipelineTaskKindServiceTest::updateAppliesParamDefaultsAndVisibility; TaskKindPages.test PIPE-089 |  |
| PIPE-090 | P2 | 自动化 | PipelineTaskKindServiceTest::updateAppliesParamDefaultsAndVisibility |  |
| PIPE-091 | P1 | 自动化 | PipelineTaskKindServiceTest::publishAssignsFirstVersionThenPatch; Controller publishReturnsVersion; TaskKindPages PIPE-091 |  |
| PIPE-092 | P1 | 自动化 | PipelineTaskKindServiceTest::publishAssignsFirstVersionThenPatch |  |
| PIPE-093 | P1 | 自动化 | TaskKindVersionsTest::explicitVersionMustBeSemver; TaskKindPages PIPE-093 |  |
| PIPE-094 | P1 | 自动化 | PipelineTaskKindServiceTest::publishRejectsDuplicateVersion |  |
| PIPE-095 | P2 | 自动化 | PipelineTaskKindServiceTest::publishRejectsOverlongNote |  |
| PIPE-096 | P1 | 自动化 | PipelineTaskKindServiceTest::activateRestoresSnapshotAndParams; Controller activateReturnsKind; TaskKindPages PIPE-097 |  |
| PIPE-097 | P1 | 自动化 | PipelineTaskKindServiceTest::activateRejectsBlankAndMissingVersion; Controller; TaskKindPages |  |
| PIPE-098 | P1 | 自动化 | PipelineTaskKindServiceTest::toggleFlipsEnabledAndRejectsMissing; Controller; TaskKindPages PIPE-098 |  |
| PIPE-099 | P1 | 自动化 | PipelineTaskKindControllerTest::validateValidScript / validateInvalidScript |  |
| PIPE-100 | P2 | 自动化 | PipelineTaskKindControllerTest::validateEmptyScriptReturnsValid |  |
| PIPE-101 | P2 | 手工 | 依赖种子数据（DB 初始化），单测未加载种子 |  |
| PIPE-102 | P1 | 自动化 | TaskKindPages.test::treats ADMIN role as admin and keeps members read-only |  |
| PIPE-103 | P0 | 自动化 | PipelineRunArtifactServiceTest::uploadSavesFileAndMetadata; Controller uploadReturnsArtifact |  |
| PIPE-104 | P1 | 手工 | 缺少 file 由 Spring Multipart 绑定拒绝，未写 MockMvc 用例 |  |
| PIPE-105 | P1 | 自动化 | PipelineRunArtifactServiceTest::uploadRejectsMissingRun; Controller uploadMissingRunReturnsNotFound |  |
| PIPE-106 | P2 | 自动化 | PipelineRunArtifactServiceTest::uploadAppliesDefaults |  |
| PIPE-107 | P0 | 自动化 | PipelineRunArtifactServiceTest::uploadSbomTriggersParser; SbomParserServiceTest::* |  |
| PIPE-108 | P1 | 自动化 | PipelineRunArtifactServiceTest::uploadNonSbomSkipsParser |  |
| PIPE-109 | P0 | 自动化 | PipelineRunArtifactServiceTest::listByRunReturnsArtifacts / openStreamReadsStoredFile; Controller list/download |  |
| PIPE-110 | P1 | 自动化 | PipelineRunArtifactServiceTest::getSbomByRunQueriesLatest; Controller sbomReturnsLatest |  |
| PIPE-111 | P0 | 手工（仅记录缺陷） | 期望行为由 PIPE-916 的 Disabled 占位测试记录 | 无独立测试 |
| PIPE-112 | P0 | 自动化 | DependencyScanServiceTest::submitSingleCreatesPipelineAndStartsRun; Controller submitReturnsScan |  |
| PIPE-113 | P1 | 自动化 | DependencyScanServiceTest::submitSingleWithJavaStackUsesMavenPlugin |  |
| PIPE-114 | P1 | 自动化 | DependencyScanServiceTest::submitSingleWithNodeStackUsesCdxgen / Go |  |
| PIPE-115 | P1 | 自动化 | DependencyScanServiceTest::normalizeModulePath* |  |
| PIPE-116 | P1 | 自动化 | DependencyScanServiceTest::submitSingleWithModulePathAutoDetectWhenStackNull |  |
| PIPE-117 | P1 | 自动化 | PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl（扫描流水线含 CLONE） |  |
| PIPE-118 | P1 | 自动化 | DependencyScanServiceTest::submitBatchCreatesOnePipelinePerScan; Controller batchSubmitReturnsList |  |
| PIPE-119 | P2 | Disabled | DependencyScanServiceTest::submitBatchRejectsNullScans（PIPE-908） |  |
| PIPE-120 | P1 | 自动化(当前) + Disabled(期望) | Controller listTasksReturnsRows; Service listRecentReturnsSubmittedTasks（PIPE-909） |  |
| PIPE-121 | P1 | 自动化 | PipelineController 无关；实际由 DependencyComponentControllerTest::pagePassesFilters 覆盖 | Service 的 4 个测试均为 pageAggregated，归入 PIPE-122 |
| PIPE-122 | P1 | 自动化 | DependencyComponentServiceTest::pageAggregated*; Controller aggregatedReturnsRows |  |
| PIPE-123 | P0 | 自动化(降级) + 手工(live) | PodExecControllerTest::containersWithoutClusterFallbackToDb；真实 Pod 查询手工 |  |
| PIPE-124 | P1 | 自动化 | PodExecControllerTest::podNotScheduledReturnsFalse; PodExecWebSocketHandlerTest::podNotAllocatedRejected |  |
| PIPE-125 | P1 | 自动化 | PodExecControllerTest::containersWithoutClusterFallbackToDb; WS noClusterSendsErrorAndCloses |  |
| PIPE-126 | P0 | 自动化 | PodExecControllerTest::runMismatch/jobMismatch/ownership; WS runMismatch/jobMismatch |  |
| PIPE-127 | P0 | 自动化 | PodExecAuthHandshakeInterceptorTest::missingTokenRejected / invalidTokenRejected |  |
| PIPE-128 | P1 | 自动化 | PodExecAuthHandshakeInterceptorTest::validTokenPopulatesAttributes / fallbackToFirstProtocol |  |
| PIPE-129 | P0 | 自动化(部分) + 手工 | PodExecWebSocketHandlerTest::textMessageWithoutSessionIsIgnored；真实终端手工 |  |
| PIPE-130 | P1 | 手工 | 真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |  |
| PIPE-131 | P2 | 手工 | 真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |  |
| PIPE-132 | P2 | 手工 | 真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |  |
| PIPE-133 | P2 | 手工 | 真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |  |
| PIPE-134 | P3 | 手工 | 真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |  |
| PIPE-135 | P1 | 自动化 | PipelineListPage.test.tsx 服务端关键词 |  |
| PIPE-136 | P2 | 手工 | 状态快捷筛选仅当前页，未单独测试 |  |
| PIPE-137 | P1 | 自动化 | PipelineEditorPage.test.tsx PIPE-137 |  |
| PIPE-138 | P1 | 自动化 | PipelineEditorPage.test.tsx 添加任务/自动新建阶段（既有） |  |
| PIPE-139 | P1 | 自动化 | pipelineGraph.test.ts（既有） |  |
| PIPE-140 | P2 | 自动化 | toolchainCascade.test.ts（既有） |  |
| PIPE-141 | P1 | 自动化 | PipelineFlow.test.tsx 创建/保存并运行（既有） |  |
| PIPE-142 | P2 | 自动化 | PipelineEditorPage.test.tsx PIPE-142 |  |
| PIPE-143 | P1 | 自动化 | PipelineFlow.test.tsx 运行参数弹框（既有） |  |
| PIPE-144 | P1 | 自动化 | PipelineRunDetailPage.test.tsx 日志抽屉（既有） |  |
| PIPE-145 | P1 | 自动化 | PipelineRunDetailPage.test.tsx PIPE-145 |  |
| PIPE-146 | P1 | 自动化 | PipelineRunDetailPage.test.tsx PIPE-146 |  |
| PIPE-147 | P1 | 自动化 | CredentialPages.test.tsx（6 条覆盖 147；其余 3 条覆盖 149） |  |
| PIPE-148 | P1 | 自动化 | TaskKindPages.test.tsx ADMIN/只读（既有） |  |
| PIPE-149 | P2 | 自动化 | CredentialPages / PipelineList / TaskKind / Editor / Detail 空态与错误态 |  |
| PIPE-150 | P2 | 手工 | 运行历史最多 200 条为前端实现上限 |  |
| PIPE-151 | P0 | 自动化 | PipelineDefinitionServiceTest::get/update/deleteRejectsOtherTenant; Controller |  |
| PIPE-152 | P0 | 自动化 | PipelineRunServiceTest::getRejectsRunFromAnotherPipeline; Controller |  |
| PIPE-153 | P0 | 自动化 | PipelineCredentialServiceTest::getRejectsOtherTenant; Controller |  |
| PIPE-154 | P1 | 手工 | PIPE-911 凭证引用统计跨租户 | 待确认业务语义，暂不写 Disabled 测试 |
| PIPE-155 | P2 | 手工 | 任务类型当前全局无租户隔离，待产品确认 |  |
| PIPE-156 | P0 | 自动化 | CredentialCipherTest（既有）+ 凭证不泄露 + TektonCompiler 日志掩码 |  |
| PIPE-157 | P0 | Disabled | PipelineRunArtifactServiceTest::uploadShouldSanitizePathTraversalFileName（PIPE-917） |  |

## 3. Disabled 与缺陷记录

### 3.1 有 `@Disabled` / `it.skip` 测试

| PIPE | 测试 | 原因 |
|------|------|------|
| 043 | `PipelineRunQueueServiceTest::cancelShouldMarkJobsCancelled` | PIPE-903：取消运行未同步更新 `pipeline_run_job` |
| 046 | `PipelineRunQueueServiceTest::approveShouldRespectConcurrencyLimit` | PIPE-904：审批续跑绕过租户并发上限 |
| 119 | `DependencyScanServiceTest::submitBatchRejectsNullScans` | PIPE-908：`batch-submit` 的 `scans=null` 触发 NPE |
| 120 | `DependencyScanServiceTest::listRecentReturnsSubmittedTasks` | PIPE-909：`/dependency-scan/tasks` 当前返回空列表 |
| 157 | `PipelineRunArtifactServiceTest::uploadShouldSanitizePathTraversalFileName` | PIPE-917：产物文件名未清洗，存在路径穿越风险 |
| 910 | `PipelineFlow.test.tsx` 的 `it.skip` | 前端 api_select 未渲染下拉 |
| 915 | `PipelineRunServiceTest::fixedParamsShouldNotBeOverriddenByRuntime` | 运行时参数可覆盖 fixed 绑定 |
| 916 | `PipelineRunArtifactServiceTest::artifactsShouldRejectCrossTenantAccess` | 产物接口未校验租户/run 归属 |

### 3.2 仅记录缺陷、暂无测试

| PIPE | 原因 |
|------|------|
| 111 | PIPE-916 的正式用例；期望行为以 916 的 Disabled 占位测试记录 |
| 154 | PIPE-911：凭证引用统计未加租户条件，业务语义待确认，标为手工 |

## 4. 手工用例与原因

| 范围 | 原因 |
|------|------|
| PIPE-051..055、057..058、062 | 真实 Tekton 执行、日志汇聚/截断、Pod 回写、耗时刷新 |
| PIPE-072、085、101、104、136、150、155 | 产品待确认、环境相关解析、种子数据、Spring Multipart 绑定、前端实现上限 |
| PIPE-123（live 部分）、129..134 | 真实 Pod 查询、终端 exec/ephemeral/debug Pod、心跳与 resize |

## 5. 维护规则

1. 用例编号与 `docs/product/05-testing/cases/pipeline.md` 保持一致。
2. 新增测试后在“测试位置”列回填 `文件::测试方法`。
3. 修复 PIPE-9xx 后，把对应 Disabled 测试改为启用并更新本表。
4. 手工用例在版本发布前按用例文档执行，不并入自动化通过数。
