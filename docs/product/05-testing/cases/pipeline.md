# 流水线测试用例

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成（核心 + 边界/异常/权限；可模拟部分已自动化，真实集群/E2E 保留手工）
> 数据来源：`backend/pipeline-core/`、`backend/server/.../ws/`、`frontend/src/modules/pipeline/`、`deploy/charts/backend/files/db/05-pipeline-schema.sql`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：15 条核心用例 |
| v0.2 | 2026-10-17 | 扩充为 157 条正式用例 + 17 条已知差异：覆盖定义校验、运行时参数、审批/状态机、并发、执行日志、凭证、任务市场、产物/SBOM、依赖扫描、Pod 终端、前端交互与多租户隔离；补充现有自动化测试映射 |
| v0.3 | 2026-10-18 | 回填自动化落点：127 条纯自动化、4 条部分自动化、4 条 Disabled、22 条手工（含 PIPE-111 仅记录缺陷）；详见功能目录 test-cases.md |

---

## 1. 范围与约定

- 编号：`PIPE-NNN`，与 [test-spec.md](../test-spec.md) 一致；`PIPE-9xx` 为已知实现差异/待确认项，不计入准出。
- 优先级：P0 主流程/数据安全，P1 主要功能/异常，P2 边界，P3 体验。
- 状态枚举（run/job）：`QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`CANCELLED`。
- 自动化映射写在“自动化”列，格式 `测试类::方法`；`待补` 表示需新增测试。

### 1.1 现有自动化测试映射

| 测试类 | 覆盖内容 |
|--------|----------|
| `PipelineGraphValidatorTest` | 图校验：空图、CLONE 唯一、审批独占、镜像并行；审批分段计划（ApprovalPlan） |
| `PipelineRunServiceTest` | 无集群失败、审批状态与连续审批、首审批后提交、hidden 参数 |
| `PipelineTaskKindServiceTest` | 版本发布/激活、保存不升版本 |
| `DependencyScanServiceTest` | 扫描流水线创建、各 stack 命令、modulePath |
| `DependencyComponentServiceTest` | 组件聚合、搜索、语言过滤、排序 |
| `SbomParserServiceTest` | CycloneDX 解析、去重、upsert |
| `TektonCompilerTest` | 任务编译、镜像、日志掩码、模板替换 |
| `TektonPipelineExecutorTest` | 状态/时间戳同步、等待诊断 |

### 1.2 环境与数据准备

| 项目 | 要求 |
|------|------|
| 执行集群 | k3s（macOS 容器内）或 Docker Desktop（Windows）；Tekton 已安装 |
| 后端配置 | `PIPELINE_KUBECONFIG` 指向可达集群；`PIPELINE_NAMESPACE=hfwas-pipeline` |
| 并发配置 | 默认 `pipeline.max-concurrent-runs=10`；并发用例可调小 |
| Git 仓库 | 一个可公开访问或带凭证访问的仓库；包含可构建的最小 Java/Node/Go 项目 |
| 镜像仓库 | Harbor 可用（镜像构建/推送用例） |
| 用户 | 平台 `admin` 与普通 `user`；两个租户用于隔离用例 |
| 流水线样例 | ①最小 BUILD ②含 CLONE+BUILD+IMAGE ③以审批开头 ④连续两个审批 ⑤多阶段多任务 |
| 凭证样例 | PASSWORD、TOKEN、KUBECONFIG 各一个；一个被流水线引用 |
| 产物样例 | 合法 CycloneDX SBOM JSON、普通文件、含 `../` 的文件名 |

## 2. 流水线定义与校验

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-001 | 创建最小流水线 | 已登录，集群可用 | 新建：name=`demo`，1 阶段 1 任务（BUILD，command 非空），无 CLONE | 创建成功；详情/列表回显一致；run 可触发 | P0 | ✅ `PipelineDefinitionServiceTest::createPersistsPipelineStagesAndJobs; PipelineControllerTest::createReturnsId` |
| PIPE-002 | 创建含 CLONE 的流水线 | — | 1 阶段 1 任务 kind=CLONE，repoUrl=`https://git.example.com/a/b.git` | 保存成功；gitRef 为空时落库为 `main` | P0 | ✅ `PipelineDefinitionServiceTest::createPersistsPipelineStagesAndJobs; PipelineControllerTest::createReturnsId` |
| PIPE-003 | 名称为空 | — | name 为空或全空格 → 保存 | 拒绝，10002「名称不能为空」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsBlankName; PipelineControllerTest::createRejectsBlankName` |
| PIPE-004 | 含 CLONE 但 repoUrl 为空 | — | kind=CLONE，repoUrl 空 | 拒绝（GitRemote 校验，提示需 http/https 地址） | P1 | ✅ `PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl` |
| PIPE-005 | repoUrl 非法 | — | `not-a-url`、`ftp://x/y`、`https://host`（无 path） | 分别拒绝，提示缺少协议/host/path | P1 | ✅ `GitRemoteTest::*（既有）` |
| PIPE-006 | 无 CLONE 时 repoUrl 为空 | — | 仅 BUILD 任务，repoUrl 空 | 允许保存（repoUrl 仅在 CLONE 时强校验） | P2 | ✅ `PipelineDefinitionServiceTest::createWithoutCloneAllowsEmptyRepoUrl` |
| PIPE-007 | 未知任务类型 | — | 提交 kind=`UNKNOWN` | 拒绝，10002「未知任务类型」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsUnknownKind` |
| PIPE-008 | 至少一个阶段 | — | stages 为空 | 拒绝，10002「流水线至少需要一个阶段」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsEmptyStages` |
| PIPE-009 | 阶段必须有任务 | — | 1 阶段 0 任务 | 拒绝，10002「阶段「x」至少需要一个任务」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsStageWithoutJobs` |
| PIPE-010 | 阶段/任务名称必填 | — | 阶段名或任务名为空 | 拒绝，对应中文提示 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsBlankStageName; ::createRejectsBlankJobName` |
| PIPE-011 | CLONE 全局唯一 | — | 两个阶段各一个 CLONE | 拒绝，10002「流水线至多一个 clone 任务」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsSecondClone` |
| PIPE-012 | 审批任务独占阶段 | — | 同一阶段含 APPROVAL + BUILD | 拒绝，10002「审批任务必须独占一列」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsApprovalSharingStage` |
| PIPE-013 | 同阶段镜像构建唯一 | — | 同一阶段两个 IMAGE | 拒绝，10002「镜像构建不能与其它镜像构建并行」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsParallelImage` |
| PIPE-014 | 命令必填 | — | BUILD/TEST/IMAGE 等 command 为空 | 拒绝，10002「任务「x」需要命令」 | P1 | ✅ `PipelineDefinitionServiceTest::createRejectsMissingCommand` |
| PIPE-015 | CLONE/APPROVAL 无需命令 | — | 两者 command 为空 | 允许保存 | P2 | ✅ `PipelineGraphValidatorTest::allowsShellOnlyWithoutClone（既有）` |
| PIPE-016 | 更新覆盖 stage/job | 已有流水线 | 修改阶段/任务并保存 | 定义更新；旧 stage/job 被删除重建；id 以路径为准 | P1 | ✅ `PipelineDefinitionServiceTest::updateDeletesChildrenBeforePersist; PipelineControllerTest::updatePassesPathId` |
| PIPE-017 | 分页默认值 | 存在 >10 条 | 不传 pageNo/pageSize | pageNo=1、pageSize=10；上限 100 | P2 | ✅ `PipelineDefinitionServiceTest::pageSizeIsCapped; ::pageUsesTenantDefaultsAndKeyword` |
| PIPE-018 | 分页排序/搜索 | 多条流水线 | keyword 匹配名称 | 仅本租户；name like；updateTime desc | P1 | ✅ `PipelineDefinitionServiceTest::pageSizeIsCapped; ::pageUsesTenantDefaultsAndKeyword` |
| PIPE-019 | 删除流水线 | 存在流水线 | DELETE `/{id}` | 流水线逻辑删除；列表不可见；历史 run 仍在库（当前不级联） | P1 | ✅ `PipelineDefinitionServiceTest::deleteRemovesChildrenAndPipeline; PipelineControllerTest::deleteReturnsOk` |
| PIPE-020 | 删除不存在/他人流水线 | — | DELETE 不存在 id | 10003「流水线不存在」 | P1 | ✅ `PipelineDefinitionServiceTest::deleteRejectsOtherTenant; PipelineControllerTest::deleteMissingReturnsNotFound` |

## 3. 运行时参数

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-021 | 无参数直接运行 | 流水线无 runtime 参数 | 点击运行 | 立即创建 run 并跳转运行详情 | P0 | ✅ `PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady` |
| PIPE-022 | 默认参数预填 | 任务类型参数有 default | 打开运行弹框 | 默认值已填入 | P0 | ✅ `runParams.test::PIPE-022; PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady` |
| PIPE-023 | 必填参数为空 | 参数 required=true | 不填 → 运行 | 拒绝，10002「运行时参数 [label] 为必填项」 | P1 | ✅ `PipelineRunServiceTest::startRejectsMissingRequiredRuntimeParam; runParams.test::PIPE-023/024` |
| PIPE-024 | 必填参数为空白 | — | 传入 `""` / `"   "` | 视为未填，拒绝 | P2 | ✅ `PipelineRunServiceTest::startRejectsMissingRequiredRuntimeParam; runParams.test::PIPE-023/024` |
| PIPE-025 | select 值合法 | 参数 type=select，options=[a,b] | 传 `a` | 运行成功，任务收到 `a` | P1 | ✅ `PipelineRunServiceTest::startRejectsSelectValueOutsideOptions` |
| PIPE-026 | select 值非法 | 同上 | 传 `c` | 拒绝，10002「值不在可选范围内」 | P1 | ✅ `PipelineRunServiceTest::startRejectsSelectValueOutsideOptions` |
| PIPE-027 | 写死参数 | binding mode=fixed | 运行弹框不展示；通过 API 传同 key 的值 | 弹框不可改；当前 API 实现会用用户值覆盖 fixed 值（是否符合预期见 PIPE-915） | P1 | 部分自动化：PipelineJobParamServiceTest::createKeepsFixedMode；PIPE-915 Disabled； |
| PIPE-028 | hidden 参数 | 参数 visibility=hidden | 弹框不显示；用户仍传同 key | 最终使用任务市场默认值（hidden 覆盖用户值） | P1 | ✅ `PipelineRunServiceTest::hiddenParamsStayOutOfRunDialogAndKeepMarketplaceDefault` |
| PIPE-029 | GIT_REF 参数 | 流水线 gitRef=dev | 运行传 GIT_REF=feature/x | run.gitRef=feature/x；否则取流水线 gitRef | P1 | ✅ `PipelineRunServiceTest::startUsesGitRefOverrideAndReplacesCmdPlaceholder` |
| PIPE-030 | CMD_ 占位替换 | command 含 `${CMD_VERSION}` | 传 CMD_VERSION=1.2.3 | run_job.command 替换为 1.2.3 | P1 | ✅ `PipelineRunServiceTest::startUsesGitRefOverrideAndReplacesCmdPlaceholder` |
| PIPE-031 | 非 CMD_ 占位不替换 | command 含 `${OTHER}` | 传 OTHER=x | 命令保持原样 | P2 | ✅ `TektonCompilerTest::commandPlaceholderNotBashVariable（既有）` |
| PIPE-032 | 参数持久化 | 运行后 | 查询 run 详情 | `runtimeParams` 保存最终生效值 | P2 | ✅ `PipelineJobParamServiceTest::createDefaultsAndBackfillsId / getParamsGroupedByJob; PipelineJobParamControllerTest::*` |
| PIPE-033 | 参数跨任务作用域 | 两个任务同 kind | 只填一次参数 | 两个任务都使用该值（按 kind 合并） | P2 | ✅ `PipelineJobParamServiceTest::createDefaultsAndBackfillsId / getParamsGroupedByJob; PipelineJobParamControllerTest::*` |

## 4. 运行状态机、审批与并发

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-034 | 未配置执行集群 | 无 `pipeline.kubeconfig` | 触发运行 | run=FAILED；error 含「未配置执行集群」；所有 job=FAILED 且写日志 | P0 | ✅ `PipelineRunServiceTest::startWithoutClusterFailsWithReadableMessage` |
| PIPE-035 | 首个阶段为审批 | 图以 APPROVAL 开头 | 触发运行 | run=WAITING_APPROVAL；审批 job=WAITING_APPROVAL；后续 job=QUEUED | P1 | ✅ `PipelineRunServiceTest::startWaitsWhenFirstStageIsApproval` |
| PIPE-036 | 非审批开头 | 首个阶段无审批 | 触发运行，未超并发 | run=QUEUED → RUNNING；segmentIndex=0 | P0 | ✅ `PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady` |
| PIPE-037 | 审批通过后继续 | run=WAITING_APPROVAL | 调用 approve（无请求体） | 当前审批 job=SUCCEEDED；下一段提交；run=QUEUED/RUNNING | P0 | ✅ `PipelineRunServiceTest::approveSubmitsSegmentAfterLeadingGate` |
| PIPE-038 | 连续审批 | 两个相邻审批阶段 | 依次 approve | 第一次后仍 WAITING_APPROVAL，且下一审批 job 被标记等待；第二次后继续 | P1 | ✅ `PipelineRunServiceTest::approveConsecutiveGatesStayWaiting` |
| PIPE-039 | 非待审批时 approve | run=RUNNING/SUCCEEDED | 调用 approve | 拒绝，10002「当前运行不在待审批」 | P1 | ✅ `PipelineRunServiceTest::approveWhenNotWaitingRejected; PipelineControllerTest::approveRejectedReturnsBizCode` |
| PIPE-040 | 审批后无后续段 | 最后一段为审批 | approve | run=SUCCEEDED，finishedAt 有值 | P1 | ✅ `PipelineRunServiceTest::approveLastGateCompletesRun` |
| PIPE-041 | 取消运行中 | run=QUEUED/RUNNING | cancel | run=CANCELLED；finishedAt 有值；集群侧 best-effort 取消 | P0 | ✅ `PipelineRunQueueServiceTest::cancelRunningMarksCancelledAndCallsExecutor; PipelineControllerTest::cancelReturnsRun` |
| PIPE-042 | 取消终态运行 | run=SUCCEEDED/FAILED/CANCELLED | cancel | 原样返回，状态不变 | P2 | ✅ `PipelineRunQueueServiceTest::cancelTerminalIsNoop` |
| PIPE-043 | 取消后 job 状态 | run 运行中多 job | cancel 后查询 job | 当前实现仅更新 run，job 行可能仍为 RUNNING/QUEUED（见 PIPE-903） | P1 | Disabled： |
| PIPE-044 | 并发上限 | `pipeline.max-concurrent-runs=2`，租户已有 2 个 RUNNING | 触发第 3 个 | 新 run 保持 QUEUED，不提交 | P1 | ✅ `PipelineRunQueueServiceTest::startKeepsQueuedWhenConcurrencyLimitReached` |
| PIPE-045 | 队列出队顺序 | 多个 QUEUED | 一个 RUNNING 结束 | 按 id 最早者出队执行（FIFO） | P1 | ✅ `PipelineRunQueueServiceTest::dequeueSubmitsOldestQueuedRun / dequeueWithoutQueuedRunIsNoop / dequeueSkipsEmptyGraph` |
| PIPE-046 | 审批续跑绕过并发 | 已超并发且有 WAITING_APPROVAL | approve | 下一段直接提交（当前路径不检查并发上限，见 PIPE-904） | P2 | Disabled： |
| PIPE-047 | 运行触发人/方式 | 已登录 | 触发运行 | trigger=MANUAL；triggeredByName=当前显示名；createBy=当前用户 | P2 | ✅ `PipelineRunServiceTest::startQueuesAndSubmitsWhenClusterReady（trigger/triggeredBy）` |
| PIPE-048 | 运行详情归属 | run 不属于该 pipeline | GET `/{pipelineId}/runs/{runId}` | 10003「运行记录不存在」 | P1 | ✅ `PipelineRunServiceTest::getRejectsRunFromAnotherPipeline; PipelineControllerTest::getRunMissingReturnsNotFound` |
| PIPE-049 | 运行分页 | 多次运行 | GET `/{id}/runs` | 按 id 倒序；不返回 jobs | P2 | ✅ `PipelineRunServiceTest::pageRunsReturnsRowsWithoutJobs` |
| PIPE-050 | 默认参数接口 | 配置了 runtime 参数 | GET `/runs/default-params` | 仅返回 runtime 绑定且非 hidden 的参数；含默认值；api_select 预取 options | P1 | ✅ `PipelineRunServiceTest::hiddenParams...; PipelineJobParamServiceTest::getDefaultParams*; PipelineControllerTest::defaultParamsReturnsList` |

## 5. 执行、状态回写与日志

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-051 | 状态轮询 | Tekton 运行中 | 观察 2 秒轮询 | run/job 状态与 Tekton 一致 | P0 | 手工：需本地 k3s/Tekton |
| PIPE-052 | step 成功 | Step exitCode=0 | 等待结束 | job=SUCCEEDED | P0 | 手工：需本地 k3s/Tekton |
| PIPE-053 | step 失败 | 非 0 退出 | 等待结束 | job=FAILED；run=FAILED；错误信息落库 | P0 | 手工：需本地 k3s/Tekton |
| PIPE-054 | Tekton 取消 | 集群侧条件 Succeeded=False 且 reason 含 cancel | 等待轮询 | run=CANCELLED | P1 | 手工：需本地 k3s/Tekton |
| PIPE-055 | 日志按容器汇聚 | 多 step 任务 | 查看运行详情日志 | 按阶段顺序拼接各容器日志 | P1 | 手工：需本地 k3s/Tekton |
| PIPE-056 | 日志脱敏 | 日志含凭证/SONAR_TOKEN/COSIGN_PRIVATE_KEY/S3_SECRET_KEY | 查看日志 | 敏感值被掩码 | P0 | ✅ `TektonCompilerTest::masksSecretInLogs（既有）` |
| PIPE-057 | 日志截断 | 单容器日志 >512KB | 查看日志 | 截断到 512KB，不导致接口超时 | P2 | 手工：需本地集群 |
| PIPE-058 | Pod 信息回写 | 任务调度成功 | 查看 run_job | podName/namespace/containers 有值 | P2 | 手工：需本地集群 |
| PIPE-059 | commitSha 回写 | CLONE 日志含 commit 标记 | 运行结束 | run.commitSha 与日志一致 | P1 | ✅ `DnsNamesTest::parsesCommitMarkerFromCloneLog（既有）` |
| PIPE-060 | 无日志诊断 | Pod 镜像拉取等待 | 查看日志 | 显示 image pull waiting 诊断 | P2 | ✅ `TektonPipelineExecutorTest::stepDiagnosticsIncludesImagePullWaiting（既有）` |
| PIPE-061 | 时间戳稳定性 | 多次轮询 | 比较 startedAt/finishedAt | finishedAt 不被后续轮询推移；QUEUED 清空时间 | P2 | ✅ `TektonPipelineExecutorTest::jobTimesUseTektonStepTimestamps 等（既有）` |
| PIPE-062 | 运行中耗时 | run=RUNNING | 打开运行详情 | 前端每秒刷新耗时 | P3 | 手工：前端每秒耗时刷新，需 E2E/视觉验证 |

## 6. 凭证管理

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-063 | 创建 PASSWORD 凭证 | — | name、kind=PASSWORD、username、secret | 创建成功；列表可见 | P0 | ✅ `PipelineCredentialServiceTest::createPasswordEncryptsSecret; PipelineCredentialControllerTest::saveReturnsId` |
| PIPE-064 | 创建 TOKEN 凭证 | — | username 为空 | username 默认 `x-access-token` | P1 | ✅ `PipelineCredentialServiceTest::createTokenDefaultsUsername` |
| PIPE-065 | 创建 KUBECONFIG 凭证 | — | kind=KUBECONFIG、secret=内容 | 创建成功；secret 加密存储 | P0 | ✅ `PipelineCredentialServiceTest::createKubeconfigWithoutUsername` |
| PIPE-066 | 非法类型 | — | kind=SSH | 拒绝，10002「类型必须是 PASSWORD、TOKEN 或 KUBECONFIG」 | P1 | ✅ `PipelineCredentialServiceTest::createRejectsUnsupportedKind; PipelineCredentialControllerTest::saveRejectsUnsupportedKind` |
| PIPE-067 | PASSWORD 缺用户名 | — | username 空 | 拒绝，10002「账号密码凭证需要用户名」 | P1 | ✅ `PipelineCredentialServiceTest::createPasswordRequiresUsername` |
| PIPE-068 | 创建时密钥为空 | — | secret 空 | 拒绝，10002「密钥不能为空」 | P1 | ✅ `PipelineCredentialServiceTest::createRequiresSecret` |
| PIPE-069 | 更新时密钥留空 | 已有凭证 | 只改名称，secret 空 | 保存成功；原密文保留 | P1 | ✅ `PipelineCredentialServiceTest::updateWithBlankSecretKeepsCiphertext` |
| PIPE-070 | 更新时传新密钥 | 已有凭证 | secret=新值 | 密文更新；明文不落库 | P0 | ✅ `PipelineCredentialServiceTest::updateWithSecretReencrypts` |
| PIPE-071 | 凭证不返回密钥 | 已有凭证 | 列表/详情/分页 | 响应不含 secret 字段 | P0 | ✅ `PipelineCredentialServiceTest::getAndDecryptNeverExposeSecretInVo; PipelineCredentialControllerTest::listReturnsCredentials` |
| PIPE-072 | 重名校验 | 已存在同名凭证 | 再创建同名 | 当前实现允许重名（DB 无唯一约束，见 PIPE-905） | P2 | 手工：当前允许重名（DB 无唯一约束），待产品确认 |
| PIPE-073 | 被引用时删除 | 凭证被流水线引用 | DELETE | 拒绝，10002「凭证仍被流水线引用，无法删除」 | P0 | ✅ `PipelineCredentialServiceTest::deleteRejectsReferencedCredential; Controller deleteReferencedReturnsBizCode` |
| PIPE-074 | 未被引用时删除 | — | DELETE | 逻辑删除，列表不可见 | P1 | ✅ `PipelineCredentialServiceTest::deleteRemovesUnreferencedCredential; Controller deleteReturnsOk` |
| PIPE-075 | 删除不存在/跨租户 | — | DELETE 他人 id | 10003「凭证不存在」 | P1 | ✅ `PipelineCredentialServiceTest::getRejectsOtherTenant; Controller getOtherTenantReturnsNotFound` |
| PIPE-076 | 分页搜索 | 多凭证 | keyword 匹配名称 | 仅本租户；按 updateTime desc | P2 | ✅ `PipelineCredentialServiceTest::pageUsesDefaults; Controller pageReturnsPage` |

## 7. 任务市场与任务类型

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-077 | 任务类型列表 | 种子数据已初始化 | GET `/pipeline/task-kinds` | 返回全部类型（含禁用），按分组/排序 | P0 | ✅ `PipelineTaskKindControllerTest::listReturnsKinds; PipelineTaskKindServiceTest::listAllIncludesDisabledKinds` |
| PIPE-078 | 详情不存在 | — | GET `/{kind}` 不存在 | 10003「Task 类型不存在: x」 | P1 | ✅ `PipelineTaskKindControllerTest::getMissingReturnsNotFound` |
| PIPE-079 | 编辑基础信息 | admin | 修改 label/description/hint/sortOrder | 保存成功；不回写 version | P0 | ✅ `PipelineTaskKindServiceTest::saveDoesNotChangeVersion; Controller updateReturnsOk` |
| PIPE-080 | label 必填 | admin | label 空 | 参数校验失败（@NotBlank） | P1 | ✅ `PipelineTaskKindControllerTest::updateRejectsBlankLabel（@Valid）` |
| PIPE-081 | 分组为空 | admin | taskGroup=`""` | 拒绝，10002「分组不能为空」 | P1 | ✅ `PipelineTaskKindServiceTest::updateRejectsBlankTaskGroup` |
| PIPE-082 | toolImage 空不覆盖 | 已有 toolImage | 传 null | 原值保留 | P2 | ✅ `PipelineTaskKindServiceTest::updateKeepsToolImageWhenDtoFieldIsNull` |
| PIPE-083 | 资源限制合法 | admin | cpuRequest=100m、memoryLimit=1Gi | 保存成功 | P1 | ✅ `PipelineTaskKindServiceTest::updateRejectsCpuOverPlatformLimit / updateRejectsMemoryOverPlatformLimit` |
| PIPE-084 | 资源限制超上限 | 配置了上限 | 传入超限值 | 拒绝并提示上限 | P1 | ✅ `PipelineTaskKindServiceTest::updateRejectsCpuOverPlatformLimit / updateRejectsMemoryOverPlatformLimit` |
| PIPE-085 | 资源格式非法 | admin | cpu=`abc` | 拒绝，提示 K8s Quantity 格式 | P2 | 手工：fabric8 Quantity 解析行为随环境，未自动化 |
| PIPE-086 | 参数 key 格式 | admin | key=`1abc` / `a-b` | 拒绝，需匹配 `[A-Za-z_][A-Za-z0-9_]*` | P1 | ✅ `PipelineTaskKindServiceTest::updateRejectsInvalidParamKey` |
| PIPE-087 | 参数 key 重复 | 同一 kind | 两个相同 key | 拒绝，10004 | P1 | ✅ `PipelineTaskKindServiceTest::updateReplacesParamsAndRejectsDuplicateKey` |
| PIPE-088 | 参数类型枚举 | admin | type=`radio` | 拒绝，10002「参数类型只支持 input、select、api_select」；空值默认 input | P1 | ✅ `PipelineTaskKindServiceTest::updateRejectsUnsupportedParamType` |
| PIPE-089 | 参数可见性 | admin | visibility=hidden/user | hidden 不出现在运行弹框；user 可见 | P1 | ✅ `PipelineTaskKindServiceTest::updateAppliesParamDefaultsAndVisibility; TaskKindPages.test PIPE-089` |
| PIPE-090 | 参数全量替换 | 已有参数 | 提交部分参数 | 未提交的旧参数被软删（全量替换语义） | P2 | ✅ `PipelineTaskKindServiceTest::updateAppliesParamDefaultsAndVisibility` |
| PIPE-091 | 发布首个版本 | 无版本 | POST versions（version 空） | 生成 `1.0.0` | P1 | ✅ `PipelineTaskKindServiceTest::publishAssignsFirstVersionThenPatch; Controller publishReturnsVersion; TaskKindPages PIPE-091` |
| PIPE-092 | 发布递增版本 | 当前 1.0.0 | version 空再发布 | 生成 `1.0.1` | P1 | ✅ `PipelineTaskKindServiceTest::publishAssignsFirstVersionThenPatch` |
| PIPE-093 | 显式版本非 semver | — | version=`v1` | 拒绝 | P1 | ✅ `TaskKindVersionsTest::explicitVersionMustBeSemver; TaskKindPages PIPE-093` |
| PIPE-094 | 版本重复 | 已存在 1.0.0 | 再发布 1.0.0 | 拒绝，10004 | P1 | ✅ `PipelineTaskKindServiceTest::publishRejectsDuplicateVersion` |
| PIPE-095 | 版本备注超长 | — | note >500 字符 | 拒绝，10002 | P2 | ✅ `PipelineTaskKindServiceTest::publishRejectsOverlongNote` |
| PIPE-096 | 激活版本 | 有多版本 | activate 指定版本 | 概览与参数恢复为该版本快照 | P1 | ✅ `PipelineTaskKindServiceTest::activateRestoresSnapshotAndParams; Controller activateReturnsKind; TaskKindPages PIPE-097` |
| PIPE-097 | 激活不存在版本 | — | activate `9.9.9` | 10003 | P1 | ✅ `PipelineTaskKindServiceTest::activateRejectsBlankAndMissingVersion; Controller; TaskKindPages` |
| PIPE-098 | 启停任务类型 | admin | PATCH toggle | enabled 0/1 取反；禁用后编辑器不展示 | P1 | ✅ `PipelineTaskKindServiceTest::toggleFlipsEnabledAndRejectsMissing; Controller; TaskKindPages PIPE-098` |
| PIPE-099 | 脚本语法校验 | — | validate script 合法/非法 | 合法返回 valid=true；非法返回 valid=false + errors（`bash -n`） | P1 | ✅ `PipelineTaskKindControllerTest::validateValidScript / validateInvalidScript` |
| PIPE-100 | 空脚本校验 | — | script 空 | 直接 valid=true | P2 | ✅ `PipelineTaskKindControllerTest::validateEmptyScriptReturnsValid` |
| PIPE-101 | 种子任务类型 | 初始化完成 | 查询种子 | 17 个内置类型存在；KUBECTL 在枚举中但无种子（见 PIPE-907） | P2 | 手工：依赖种子数据（DB 初始化），单测未加载种子 |
| PIPE-102 | 普通用户只读 | 非 admin 登录 | 打开任务市场 | 可查看；编辑/参数/启停不可用 | P1 | ✅ `TaskKindPages.test::treats ADMIN role as admin and keeps members read-only` |

## 8. 运行产物与 SBOM

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-103 | 上传产物 | run 存在 | POST multipart：file，type 可选 | 落盘到 artifact storage；返回产物信息 | P0 | ✅ `PipelineRunArtifactServiceTest::uploadSavesFileAndMetadata; Controller uploadReturnsArtifact` |
| PIPE-104 | 上传缺文件 | — | 不传 file | 参数错误 | P1 | 手工：缺少 file 由 Spring Multipart 绑定拒绝，未写 MockMvc 用例 |
| PIPE-105 | run 不存在 | — | 向不存在 runId 上传 | 10003 | P1 | ✅ `PipelineRunArtifactServiceTest::uploadRejectsMissingRun; Controller uploadMissingRunReturnsNotFound` |
| PIPE-106 | 默认产物类型 | — | 不传 type | 默认 `sbom` | P2 | ✅ `PipelineRunArtifactServiceTest::uploadAppliesDefaults` |
| PIPE-107 | SBOM 解析 | 上传 CycloneDX JSON | 上传后查询组件 | dependency_component 写入；同 purl 去重 | P0 | ✅ `PipelineRunArtifactServiceTest::uploadSbomTriggersParser; SbomParserServiceTest::*` |
| PIPE-108 | 非 SBOM 文件 | — | 上传普通文件 | 不解析组件或解析失败仅记日志，不影响上传 | P1 | ✅ `PipelineRunArtifactServiceTest::uploadNonSbomSkipsParser` |
| PIPE-109 | 产物列表/下载 | 已有产物 | GET list、GET download | 列表按 run 过滤；下载内容与上传一致 | P0 | ✅ `PipelineRunArtifactServiceTest::listByRunReturnsArtifacts / openStreamReadsStoredFile; Controller list/download` |
| PIPE-110 | 最新 SBOM | 多次上传 sbom | GET `/sbom` | 返回最新一条 | P1 | ✅ `PipelineRunArtifactServiceTest::getSbomByRunQueriesLatest; Controller sbomReturnsLatest` |
| PIPE-111 | 跨租户访问产物 | 他人 run | 访问产物列表/下载/SBOM | 拒绝/不可见（当前实现未校验归属，见 PIPE-916） | P0 | 手工：仅记录缺陷，期望行为由 PIPE-916 占位测试记录 |

## 9. 依赖扫描与组件

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-112 | 提交扫描（Java） | 集群可用 | submit：repoUrl、stack=JAVA_MAVEN | 创建 `[依赖扫描] repo` 流水线；3 阶段 CLONE/BUILD/DEPENDENCY_ANALYSIS；立即运行 | P0 | ✅ `DependencyScanServiceTest::submitSingleCreatesPipelineAndStartsRun; Controller submitReturnsScan` |
| PIPE-113 | Java 扫描命令 | — | 查看生成命令 | BUILD 用 `mvn dependency:resolve`；分析用 cyclonedx-maven-plugin 2.9.3 | P1 | ✅ `DependencyScanServiceTest::submitSingleWithJavaStackUsesMavenPlugin` |
| PIPE-114 | Node/Go 扫描命令 | — | stack=NODE / GO | NODE 用 `npm ci` 或 `npm install` + cdxgen；GO 用 `go mod download` + cdxgen | P1 | ✅ `DependencyScanServiceTest::submitSingleWithNodeStackUsesCdxgen` 等 |
| PIPE-115 | modulePath 前缀 | 传 `apps/web/` | 查看命令 | 命令前缀 `cd apps/web && `；尾斜杠被清理 | P1 | ✅ `DependencyScanServiceTest::normalizeModulePath*` |
| PIPE-116 | stack 为空自动检测 | stack 空 | 提交 | 分析脚本使用运行时自动检测 | P1 | ✅ `DependencyScanServiceTest::submitSingleWithModulePathAutoDetectWhenStackNull` |
| PIPE-117 | repoUrl 为空 | — | submit 无 repoUrl | 拒绝（CLONE 校验） | P1 | ✅ `PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl（扫描流水线含 CLONE）` |
| PIPE-118 | 批量提交 | 多个 scan | batch-submit | 依次创建并启动；返回各结果 | P1 | ✅ `DependencyScanServiceTest::submitBatchCreatesOnePipelinePerScan; Controller batchSubmitReturnsList` |
| PIPE-119 | 批量 scans 为 null | — | scans=null | 当前实现 NPE（见 PIPE-908） | P2 | Disabled： |
| PIPE-120 | 扫描任务列表 | 已提交 | GET `/dependency-scan/tasks` | 当前实现恒返回空列表（见 PIPE-909） | P1 | 部分自动化：Controller listTasksReturnsRows; Service listRecentReturnsSubmittedTasks（PIPE-909）； |
| PIPE-121 | 组件分页/搜索/语言 | 已有组件 | GET `/dependency/components/page` | 按 search/language 过滤，分页正确 | P1 | ✅ `DependencyComponentServiceTest::*; Controller pagePassesFilters` |
| PIPE-122 | 组件聚合 | 同一组件跨 run | GET `/aggregated` | occurrenceCount 正确；usedInRepos 最多 3 个；按出现次数降序 | P1 | ✅ `DependencyComponentServiceTest::pageAggregated*; Controller aggregatedReturnsRows` |

## 10. Pod 终端与容器查询

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-123 | 容器列表（运行中） | run/job 存在且 Pod 已调度 | GET `/containers` | 返回 namespace、podName、containers、defaultContainer | P0 | 部分自动化：PodExecControllerTest::containersWithoutClusterFallbackToDb；真实 Pod 查询手工； |
| PIPE-124 | Pod 未调度 | job 仍在 QUEUED | GET `/containers` | podExists=false；容器 unavailable 或 unknown | P1 | ✅ `PodExecControllerTest::podNotScheduledReturnsFalse; PodExecWebSocketHandlerTest::podNotAllocatedRejected` |
| PIPE-125 | 无执行集群 | 未配置 kubeconfig | GET `/containers` | 使用 DB containers，全部 unknown/unavailable | P1 | ✅ `PodExecControllerTest::containersWithoutClusterFallbackToDb; WS noClusterSendsErrorAndCloses` |
| PIPE-126 | 归属校验 | 他人 pipeline/run/job | GET `/containers` | 拒绝/10003 | P0 | ✅ `PodExecControllerTest::runMismatch/jobMismatch/ownership; WS runMismatch/jobMismatch` |
| PIPE-127 | WebSocket 握手鉴权 | — | 不带 token 或无效 token 连接 `/ws/exec/...` | 401 拒绝 | P0 | ✅ `PodExecAuthHandshakeInterceptorTest::missingTokenRejected / invalidTokenRejected` |
| PIPE-128 | 子协议 token | — | 通过 `Sec-WebSocket-Protocol` 传 JWT | 握手成功；token 原样回写子协议 | P1 | ✅ `PodExecAuthHandshakeInterceptorTest::validTokenPopulatesAttributes / fallbackToFirstProtocol` |
| PIPE-129 | 连接已运行容器 | 容器 running | 发送 `{type:input,data}` | 二进制帧返回 stdout/stderr | P0 | 部分自动化：PodExecWebSocketHandlerTest::textMessageWithoutSessionIsIgnored；真实终端手工； |
| PIPE-130 | 连接未运行容器 | terminated/waiting | 建立连接 | 自动使用 ephemeral 或 debug Pod；不可用时返回原因 | P1 | 手工：真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |
| PIPE-131 | Pod 已删除但有 PVC | 存在 `{tektonName}-ws` PVC | 建立连接 | 创建 debug Pod 并连接 | P2 | 手工：真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |
| PIPE-132 | 会话上限 | 已开 20 个会话 | 再连接 | 拒绝并提示上限 | P2 | 手工：真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |
| PIPE-133 | 心跳与空闲 | 已连接 | 不响应 pong / 空闲 30 分钟 | 90 秒后断开 / 30 分钟空闲断开 | P2 | 手工：真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |
| PIPE-134 | resize | 已连接 | 发送 `{type:resize,cols,rows}` | 终端尺寸更新 | P3 | 手工：真实 Pod 终端 exec/ephemeral/debug Pod、心跳与 resize |

## 11. 前端交互、空态与权限

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-135 | 列表搜索 | 多条流水线 | 输入关键词 | 300ms 防抖写 URL；服务端按名称搜索 | P1 | ✅ `PipelineListPage.test.tsx 服务端关键词` |
| PIPE-136 | 列表状态筛选 | 多条流水线 | 点击状态快捷筛选 | 仅筛选当前页数据 | P2 | 手工：状态快捷筛选仅当前页，未单独测试 |
| PIPE-137 | 新建校验 | 编辑器 | 不填名称/无阶段/阶段无任务 | 错误条 + toast，阻止保存 | P1 | ✅ `PipelineEditorPage.test.tsx PIPE-137` |
| PIPE-138 | 添加阶段/任务 | 编辑器 | 添加任务类型 | 按任务组展示 enabled 类型；点击即加；不合法时自动新建阶段 | P1 | ✅ `PipelineEditorPage.test.tsx 添加任务/自动新建阶段（既有）` |
| PIPE-139 | 任务约束提示 | 编辑器 | 尝试加第二个 CLONE / 审批与任务同列 / 并行 IMAGE | 阻止并提示原因 | P1 | ✅ `pipelineGraph.test.ts（既有）` |
| PIPE-140 | 工具链级联 | 编辑器高级设置 | 选择技术栈→运行时→工具版本 | 选项级联正确；未知组合不可选 | P2 | ✅ `toolchainCascade.test.ts（既有）` |
| PIPE-141 | 保存并运行 | 参数已填 | 点击保存并运行 | 先保存再打开运行弹框；无参数直接启动 | P1 | ✅ `PipelineFlow.test.tsx 创建/保存并运行（既有）` |
| PIPE-142 | 未保存离开 | 有脏数据 | 点击取消/刷新 | 弹出确认 | P2 | ✅ `PipelineEditorPage.test.tsx PIPE-142` |
| PIPE-143 | 运行参数弹框 | 有 input/select/api_select | 打开弹框 | 仅 required 为空时阻止；api_select 当前按普通输入处理（见 PIPE-910） | P1 | ✅ `PipelineFlow.test.tsx 运行参数弹框（既有）` |
| PIPE-144 | 运行详情日志 | run 运行中 | 打开日志抽屉 | 每 3 秒轮询；按阶段拼接；无 WebSocket | P1 | ✅ `PipelineRunDetailPage.test.tsx 日志抽屉（既有）` |
| PIPE-145 | 审批交互 | run=WAITING_APPROVAL | 点击通过 | 调用 approve；无“驳回”入口 | P1 | ✅ `PipelineRunDetailPage.test.tsx PIPE-145` |
| PIPE-146 | 取消交互 | run 非终态 | 点击取消并确认 | 调用 cancel；刷新缓存 | P1 | ✅ `PipelineRunDetailPage.test.tsx PIPE-146` |
| PIPE-147 | 凭证页 | — | 新建/编辑/删除 | 类型 PASSWORD/TOKEN/KUBECONFIG；编辑密钥留空不改；删除需确认 | P1 | ✅ `CredentialPages.test.tsx（9 条）` |
| PIPE-148 | 任务市场权限 | 非 admin | 打开任务类型详情 | 概览/参数只读；无编辑/启停 | P1 | ✅ `TaskKindPages.test.tsx ADMIN/只读（既有）` |
| PIPE-149 | 空态/加载/错误 | — | 触发各类状态 | 列表/详情/运行/凭证/任务市场均有对应文案 | P2 | ✅ `CredentialPages / PipelineList / TaskKind / Editor / Detail 空态与错误态` |
| PIPE-150 | 运行历史上限 | >200 条运行 | 打开历史 | 最多拉取 200 条（当前实现限制） | P2 | 手工：运行历史最多 200 条为前端实现上限 |

## 12. 多租户与安全

| 编号 | 场景 | 前置条件 | 步骤与输入 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------------|----------|--------|--------|
| PIPE-151 | 流水线跨租户 | 两租户各有流水线 | 租户 A 访问租户 B 流水线 | 10003/不可见 | P0 | ✅ `PipelineDefinitionServiceTest::get/update/deleteRejectsOtherTenant; Controller` |
| PIPE-152 | 运行跨租户 | 同上 | 触发/查看他人 run | 拒绝 | P0 | ✅ `PipelineRunServiceTest::getRejectsRunFromAnotherPipeline; Controller` |
| PIPE-153 | 凭证跨租户 | 同上 | 查询/删除他人凭证 | 10003 | P0 | ✅ `PipelineCredentialServiceTest::getRejectsOtherTenant; Controller` |
| PIPE-154 | 凭证引用统计跨租户 | 租户 A 的凭证 id 与租户 B 引用 | 删除租户 A 凭证 | 当前引用统计未加租户条件，可能误判（见 PIPE-911） | P1 | 手工：PIPE-911 凭证引用统计跨租户，业务语义待确认|
| PIPE-155 | 任务类型租户隔离 | 种子为全局数据 | 租户 A/B 查看 | 当前任务类型无租户过滤，所有租户共享（见 PIPE-912） | P2 | 手工：任务类型当前全局无租户隔离，待产品确认 |
| PIPE-156 | 凭证明文泄露 | 抓包/查日志 | 运行含凭证的任务 | 日志掩码；接口不返回 secret；DB 存密文 | P0 | ✅ `CredentialCipherTest（既有）+ 凭证不泄露 + TektonCompiler 日志掩码` |
| PIPE-157 | 产物路径穿越 | 上传文件名含 `../` | 上传产物 | 文件名被安全处理，不能逃逸存储目录（当前实现直接使用原文件名，见 PIPE-917） | P0 | Disabled： |

## 13. 已知实现差异与待确认（PIPE-9xx）

> 这些条目用于跟踪“实现与产品预期不一致”的点，修复后转为正式用例或关闭。

| 编号 | 现象 | 期望 | 处理 |
|------|------|------|------|
| PIPE-901 | 更新流水线时旧 `pipeline_job_param` 不清理 | 更新后仅保留新参数 | 待确认是否为缺陷；补删除逻辑或用例明确保留语义 |
| PIPE-902 | 删除流水线不级联删除 run/artifact | 按产品策略决定：保留审计或级联 | 待产品确认 |
| PIPE-903 | 取消运行只更新 run，run_job 仍为 RUNNING/QUEUED | 非终态 job 应置 CANCELLED | 建议修复并补测试 |
| PIPE-904 | 审批续跑路径不检查并发上限 | 与普通运行一致受并发限制 | 建议修复 |
| PIPE-905 | 凭证允许重名 | 同租户名称唯一或明确允许 | 待产品确认 |
| PIPE-906 | 后端未校验 `select` 参数至少有一个选项（前端有此校验） | 后端也应拒绝空选项的 select 参数 | 待确认并补服务端校验 |
| PIPE-907 | `KUBECTL` 在枚举中但无种子任务类型 | 补齐种子或移除枚举 | 待确认 |
| PIPE-908 | `batch-submit` 的 `scans=null` 触发 NPE | 返回参数错误 | 补空值校验 |
| PIPE-909 | `/dependency-scan/tasks` 恒返回空列表 | 返回最近扫描任务 | 实现或下线接口 |
| PIPE-910 | 后端 `getDefaultParams` 已预取 api_select options，但前端只对 `select` 渲染下拉，`api_select` 仍显示普通输入框 | 运行弹框按 options 渲染下拉 | 前端补齐 |
| PIPE-911 | 凭证“被引用”统计未加租户条件 | 仅统计当前租户引用 | 修复查询 |
| PIPE-912 | 任务类型无租户隔离 | 明确全局模板或按租户隔离 | 待产品确认 |
| PIPE-913 | 前端无 Pod 终端、产物/SBOM 下载入口 | 与后端能力对齐 | 前端补齐 |
| PIPE-914 | 运行日志为 3 秒轮询，无 WebSocket | 视产品要求决定是否实时化 | 待确认 |
| PIPE-915 | 用户通过 API 传参可覆盖 fixed 绑定值 | fixed 参数应不可被运行时覆盖 | 待产品确认并补校验 |
| PIPE-916 | 产物 list/download/sbom 未校验 run 所属租户；download 只按 artifactId 查询、未校验属于该 runId | 按租户与 run 双重归属校验 | 建议修复 |
| PIPE-917 | 产物文件名直接来自 `getOriginalFilename()` 并用于路径拼接 | 清洗文件名，禁止路径分隔符/`..` | 建议修复 |

## 14. 待补

1. 可模拟的 P0/P1 用例已完成自动化；真实集群/E2E 与 Disabled 用例见功能目录 test-cases.md。
2. 补充 Tekton 集群不可达、命名空间创建失败、Secret 创建失败等环境异常用例。
3. 补充大流水线（多阶段、多任务、分段 `segment_index`）执行用例。
4. 补充运行产物存储满、上传中断、并发上传用例。
5. 补充 WebSocket 断线重连、输入乱码、终端尺寸变化用例。
