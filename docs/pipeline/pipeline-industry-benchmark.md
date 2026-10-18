# 流水线产品行业对标与体验差距分析

> 日期：2026-10-18
> 版本：v0.1
> 状态：调研稿（用于五年规划与前端改版决策）
> 范围：CI/CD、Kubernetes 原生工作流、平台工程与 AI 辅助交付
> 配套文档：[`pipeline-5year-roadmap.md`](./pipeline-5year-roadmap.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：13 类产品/平台画像、信息架构与关键旅程对照、20 个 UX 模式、2026-2031 趋势、能力差距矩阵与参考链接 |

---

## 1. 调研范围与方法

### 1.1 调研问题

1. 成熟 CI/CD 产品如何组织“流水线 → 运行 → 日志 → 产物 → 环境”的信息架构？
2. 从创建到首次成功运行，成熟产品如何降低配置成本？
3. 失败排障、审批、发布、回滚的关键页面流转是什么？
4. 2026-2031 年，哪些能力会成为交付平台的“默认项”而不是“加分项”？
5. 哪些模式适合 HFWAS 的私有化、多租户、PM 一体化定位？

### 1.2 方法

- 阅读官方文档与产品文档（GitHub Docs、GitLab Docs、Tekton、Argo、Harness、Azure DevOps、CircleCI、Buildkite、Dagger、Backstage、SLSA、DORA 等）。
- 参考公开的产品截图、Demo 与 GitHub 仓库说明。
- 结合本地现状（`frontend/src/modules/pipeline/`、`docs/pipeline/`、`docs/product/02-features/modules/pipeline.md`）做差距映射。
- 结论按“可直接借鉴 / 需要改造 / 不适合”三档标注，避免照搬。

### 1.3 说明

- 本文不追求功能清单的穷举，而是提取**页面组织方式、交互模式与产品演进方向**。
- 产品功能与定价会变化，引用以官方文档入口为准；具体版本以查阅日期为准。
- 标注“趋势”的内容带有判断成分，已在第 7 节给出置信度。

### 1.4 关键行业数据（2026）

以下数据来自 JetBrains《State of CI/CD 2026》与 DORA/Faros 2025 相关公开分析，用于校准本规划的方向与优先级：

| 数据 | 数值 | 含义 |
|------|------|------|
| 组织采用率 | GitHub Actions 47%、GitLab CI 32%、Jenkins 28%、Azure DevOps Server 15%、TeamCity 10% | 市场仍是“三强 + 长尾” |
| 多工具并存 | 25% 组织同时使用 2 套以上 CI/CD，17% 使用 3 套以上 | 工具链整合是换工具的前三大原因 |
| 自托管 | 72.8% 的主 CI/CD 跑在自建/自管云上，纯 on-prem 30.5%，完全托管 24.4% | 私有化与混合部署仍是主流需求 |
| AI 使用位置 | 48.8% 至少在一个 CI/CD 环节使用 AI；22% 使用 AI agent；仅 8% 在构建步骤内运行 AI step | AI 主要在“周边”，尚未进入执行内核 |
| MCP / agent skills | 28% 已通过 MCP 或 agent skills 访问 CI/CD 工具 | 工具互操作层正在形成 |
| AI 变更的副作用 | 37% 称 AI 变更量拖慢 review；36% 难以判断变更可靠性；28% 面临不可预测成本 | “AI 生产力悖论”：产出增加，验证与治理成为瓶颈 |

> 结论：**自托管 / 私有化不是过渡形态，而是长期主流；AI 的机会在“验证、解释、治理”，而不只是“生成 YAML”。**

---

## 2. 结论摘要

1. **成熟产品的共同点不是“功能多”，而是“运行中心化”。** 用户从任意入口最终都落到一个 Run 页面：阶段/步骤、日志、产物、审批、重试、部署结果都在同一上下文里。
2. **创建成本由模板与约束决定。** GitHub Actions 用 Marketplace + reusable workflows，GitLab 用 CI/CD Components 与模板，Backstage 用 Software Templates，Harness 用 Pipeline Templates；HFWAS 的任务市场只解决了“任务”，还没有解决“整条流水线”。
3. **排障体验由“实时日志 + 失败摘要 + 一键重试”决定。** 轮询、纯文本、无搜索的日志在 2026 年已经是明显短板。
4. **CD 的重心是环境与发布，不是脚本。** Argo CD/Rollouts、Harness、GitLab Environments 都把环境、审批、漂移、回滚建模为一等对象；`kubectl apply` 不是产品能力。
5. **下一阶段竞争点是工程智能与 AI。** DORA/CI 健康度/Flaky Test/成本分析正在从“高级报表”变成“默认能力”；AI 从补全、解释走向受控代理。
6. **HFWAS 的机会在“控制面 + 私有化 + PM 闭环”，不在重造 runtime 或 Marketplace 规模。**

---

## 3. 产品画像

### 3.1 GitHub Actions

| 维度 | 观察 |
|------|------|
| 定位 | 与代码同屏的托管 CI/CD，Marketplace 生态最大 |
| 配置 | `.github/workflows/*.yml`；workflow → job → step；支持 matrix、needs、reusable workflows（最多 10 层嵌套、单次运行 50 次调用）、composite actions、workflow lockfiles |
| 页面 IA | 仓库 → Actions 页签 → Workflow 列表 → Run 列表 → Run 详情（实时 Job 图 + 步骤日志 + artifacts/attestations） |
| 运行视图 | 左侧实时 DAG，节点显示状态与依赖线；点 job 看日志；失败步骤高亮；支持 rerun all / rerun failed jobs；300+ job 的渲染与过滤在 2026 年才补齐 |
| 审批/环境 | Environments + protection rules：required reviewers（最多 6 个用户/团队）、wait timer、分支限制、prevent self-review、GitHub App 自定义保护规则、`pending_deployments` API |
| 产物 | Artifacts、Cache、artifact attestations（Sigstore provenance）、SBOM、OIDC 换云凭证 |
| AI | Copilot coding agent；Agentic Workflows（2026-02 technical preview）：Markdown 描述任务，编译为标准 workflow，默认只读、网络隔离、SHA 固定、通过 safe outputs 写操作 |
| 优势 | 与代码/PR 无缝；模板与 Marketplace；日志与运行体验成熟；OIDC/attestations 领先；规模验证充分 |
| 短板 | 强绑 GitHub；托管成本；Marketplace 供应链风险；无可视化 DAG 编辑器；复杂 DAG 表达力有限 |
| 可借鉴 | Run 中心、步骤日志、rerun failed、environments/approvals、reusable workflow、OIDC、attestation、Agentic Workflow 的“只读默认 + safe outputs”安全模型 |
| 不照搬 | Marketplace 规模竞赛；YAML 作为唯一入口 |

参考：<https://docs.github.com/en/actions>

### 3.2 GitLab CI/CD

| 维度 | 观察 |
|------|------|
| 定位 | 一站式 DevSecOps，SCM + CI + 安全 + 制品 + 环境 |
| 配置 | `.gitlab-ci.yml`；stage/job、needs（DAG）、rules、include、components、matrix；CI/CD Catalog 作为组件市场 |
| 页面 IA | 项目 → Build → Pipeline editor / Pipelines → Pipeline 详情（阶段/DAG 视图）→ Job 日志；Secure → Security dashboard；Settings → CI/CD；Deploy → Environments |
| 运行视图 | 阶段视图与 DAG 视图切换；Job 重试；合并列车（merge trains）串行验证合并结果，支持并行上限与 auto-merge |
| 审批/环境 | Protected environments、deployment approvals、scheduled deployments；保护规则在 YAML 之外 |
| 产物 | Job artifacts、package registry、container registry、SBOM、security reports |
| AI | Duo Fix Pipeline（分析失败日志/MR/仓库上下文，给内联修复或新建 MR）；CI Expert Agent；Agentic Breaking Change Resolution；AI Governance Dashboard；MCP server |
| 优势 | 一体化；Pipeline editor 的“编辑/校验/可视化/全量展开”四件套；merge train；安全仪表盘；私有化成熟 |
| 短板 | 许可证成本；非 GitLab 仓库体验差；界面信息密度高、学习曲线陡；AI 按 credits 计费 |
| 可借鉴 | 阶段/DAG 双视图、needs、protected environments、deployment approvals、安全等级/趋势/漏斗、Duo Fix Pipeline 的“故障现场 AI” |
| 不照搬 | 整套 SCM + Issue 取代 PM；过重的企业配置 |

参考：<https://docs.gitlab.com/ee/ci/>

### 3.3 Jenkins / Blue Ocean

| 维度 | 观察 |
|------|------|
| 定位 | 自托管自动化服务器，插件生态最大 |
| 配置 | Jenkinsfile（Declarative/Scripted）、freestyle job、Shared Libraries、Snippet Generator |
| 页面 IA | Dashboard → Job → Build → Console Output；Blue Ocean 独立 UI 已于 2026-07 正式弃用 |
| 运行视图 | Pipeline Graph View 成为官方替代：可交互嵌套图、可折叠阶段、内联日志、图/阶段切换；另有 Stage View |
| 审批/环境 | `input` step 做人工卡点；**无环境一等模型**，审批、部署目标、凭证靠插件/脚本拼装 |
| 优势 | 插件多、环境适配强、存量巨大、无席位费、Pipeline-as-Code 先驱 |
| 短板 | 主节点 SPOF、插件安全与升级债、UI 碎片化、缺少现代环境/审批/供应链模型、可观测性弱 |
| 可借鉴 | Pipeline Graph View 的“嵌套阶段 + 内联日志 + 双视图”；Shared Library 的组织级复用；插件治理的反面教训 |
| 不照搬 | 插件式内核、Groovy DSL、主从架构作为新产品底座 |

参考：<https://www.jenkins.io/doc/book/blueocean/>

### 3.4 Tekton

| 维度 | 观察 |
|------|------|
| 定位 | Kubernetes 原生 CI/CD 标准（CNCF） |
| 模型 | Task → Pipeline → PipelineRun；每个 Task 一个 Pod，每步一个容器；支持 when、runAfter、finally、workspaces、results |
| 页面 IA | 本身以 CRD/YAML 与 CLI 为主，Dashboard 提供 PipelineRun/TaskRun 列表、日志、YAML、Git 导入与扩展 |
| 审批 | ManualApprovalGate：ApprovalTask + 审批配额 + `approvalsReceived`/`approversResponse` 审计线索；OpenShift 控制台有 Approvals tab，社区 Dashboard 需插件 |
| 供应链 | Chains 默认生成 SLSA provenance、Sigstore 签名、SBOM，可 OCI 编码；是 K8s 原生供应链的参考实现 |
| 优势 | 云原生、可移植、厂商中立、可组合；OpenShift Pipelines 等企业发行版 |
| 短板 | 原生 YAML 啰嗦；Dashboard 体验弱；缺少产品化控制面（模板、环境、分析）；审批/多租户需平台层补齐 |
| 可借鉴 | 执行模型、可复用 Task、workspaces/results、ManualApprovalGate 的审批配额与审计、Chains 的“构建即证明” |
| 不照搬 | 直接把 CRD 暴露给业务用户；以 YAML 作为主交互 |

参考：<https://tekton.dev/docs/>

### 3.5 Argo Workflows / Argo CD / Argo Rollouts

| 维度 | 观察 |
|------|------|
| 定位 | Kubernetes 原生工作流与 GitOps 交付套件 |
| 模型 | Workflow DAG/Steps、Template、CronWorkflow；Argo CD Application；Rollouts 金丝雀/蓝绿；Argo Events 事件源/Sensor |
| 页面 IA | Workflows 列表 → 详情 DAG + 节点日志/产物；Argo CD Applications → 资源树 + Sync Status/Health + Diff/History/Rollback；Rollouts 提供 list/individual view 与 promote/abort |
| 运行视图 | 实时 DAG、点击节点看日志、suspend/resume、retry；Artifact Visualization 可在 DAG 中直接展开图片/文本/HTML/JSON，HTML 走 CSP 沙箱 |
| 审批/环境 | Workflow 用 suspend template 做人工卡点；Rollouts 用 promote/abort；无企业级环境审批 |
| 供应链 | Argo CD 有 SSO/RBAC、源完整性、sync windows；SLSA/Sigstore 通常接 Tekton Chains 或 Argo-SLSA |
| 优势 | DAG 表达力强；GitOps 事实标准；Rollouts 渐进式发布；多集群 ApplicationSet；事件驱动 |
| 短板 | 偏平台/批处理；CI 语义需自建；UI 偏运维；权限与多租户需额外封装；供应链需外挂 |
| 可借鉴 | DAG 可视化、节点级日志/产物内联展示、GitOps 同步/健康双状态、Rollouts promote/abort、Argo Events 触发器目录 |
| 不照搬 | 把 Argo 的自由 DAG 直接作为 CI 默认模型；把 GitOps 控制器塞进流水线 |

参考：<https://argoproj.github.io/workflows/>、<https://argo-cd.readthedocs.io/>、<https://argoproj.github.io/rollouts/>

### 3.6 Harness

| 维度 | 观察 |
|------|------|
| 定位 | 企业级软件交付平台（CI、CD、Feature Flags、Chaos、SRM、AI） |
| 配置 | 可视化 Pipeline Studio + YAML 双模；Stage/Step/Connector/Environment/Service/Template/Input Set |
| 页面 IA | Pipelines / Deployments / Builds / Environments / Connectors / Templates / Policy / IDP；Execution 详情含 Stage/Step、日志、输入输出、AI 摘要 |
| 审批/环境 | 审批阶段、Jira/ServiceNow 变更单、手工/自动、策略护栏；环境与连接器是一等对象 |
| AI | Software Delivery Knowledge Graph；NL → pipeline（Architect Mode，经 MCP 访问 pipelines/templates/services/environments/connectors/secrets）；Pipeline Understanding；对话式排障；AI Verification & Rollback 接 Datadog/Dynatrace/Prometheus；Autonomous Code Maintenance |
| 优势 | 环境/服务/连接器模型完整；审批、门禁、回滚；AI 深度；企业管理与治理 |
| 短板 | 商业授权；平台较重；模块与定价复杂；AI 依赖高质量历史数据 |
| 可借鉴 | Stage/Step 执行视图、Environment/Service 模型、Pipeline Understanding、AI Verification & Rollback、知识图谱 |
| 不照搬 | 一次性做大而全的模块矩阵 |

参考：<https://www.harness.io/products/continuous-integration>

### 3.7 Azure DevOps Pipelines

| 维度 | 观察 |
|------|------|
| 定位 | 微软生态的一体化研发平台 |
| 配置 | YAML pipeline 为主 + Classic Release 可视化；templates、StringList、运行级 Full YAML |
| 页面 IA | Pipelines → Pipelines / Environments / Releases / Library；Run 详情含阶段/作业、日志、Checks 面板 |
| 审批/环境 | Environments checks 是最成熟的“资源级策略编排”：static（分支控制、必需模板、制品评估）→ pre-check approvals → dynamic（审批、Azure Function、REST API、营业时间、Azure Monitor 告警）→ post-check approvals → Exclusive Lock，按序执行 |
| 优势 | 与 Azure/工作项/测试计划一体；企业审批与合规；checks 组合灵活；on-prem Server 选项 |
| 短板 | 现代 UX 滞后；YAML-only 作者体验；AI 能力弱；Cloud/Server 功能节奏分裂 |
| 可借鉴 | checks 的分类与执行顺序、pre/post approvals、Exclusive Lock、Business Hours、保护规则不在 YAML 内 |
| 不照搬 | 双模式长期并存导致维护成本 |

参考：<https://learn.microsoft.com/en-us/azure/devops/pipelines/>

### 3.8 CircleCI

| 维度 | 观察 |
|------|------|
| 定位 | SaaS CI，强调速度与测试并行 |
| 配置 | `config.yml` + Orbs（可复用包） |
| 页面 IA | Projects → Pipelines / Insights / Orbs / Config Policies；Workflow → Job → 步骤日志 |
| 运行/洞察 | Insights 提供最近 100 次运行状态、成功率、duration p50/p95、credits 使用、job 级 credits/95th percentile/运行次数/成功率、Test Insights |
| 审批/治理 | Approval job、contexts、restricted contexts、config policies（组织级配置约束） |
| 优势 | 测试分片、缓存、Docker/ARM/GPU executor、Insights、Orbs、config policies |
| 短板 | 通用场景被 Actions 挤压；自托管能力弱；config/orb 复杂度 |
| 可借鉴 | Insights 的“成功率 + p50/p95 + credits + 下钻”、config policies、flaky tests 独立入口、SSH rerun |
| 不照搬 | 以 SaaS 分钟计费为核心商业模式 |

参考：<https://circleci.com/docs/>

### 3.9 Buildkite

| 维度 | 观察 |
|------|------|
| 定位 | 混合 CI：SaaS 控制面 + 自建 Agent |
| 配置 | `pipeline.yml` + 运行时 dynamic pipelines（任意语言生成步骤）+ plugins + templates |
| 页面 IA | Pipelines / Builds / Agents / Test Engine / Insights / Catalog |
| 运行视图 | Waterfall：灰色=等待 agent、黄色=已分配未运行、绿色/红色=成功/失败；parallel/matrix/group 嵌套行；直接区分排队与执行；上限 3,000 jobs / 1,800 steps |
| 测试智能 | Test Engine 提供 flaky 检测、quarantine、test ownership、labels、saved views |
| 优势 | 控制面 SaaS + 自托管 Agent；超大并行；dynamic pipelines；Test Engine |
| 短板 | 需自运 Agent；治理/审批弱于 Azure/Harness；dynamic pipelines 增加可复现性难度 |
| 可借鉴 | Waterfall 三段着色、Test Engine 的 flaky/quarantine/ownership、dynamic pipelines、MCP server |
| 不照搬 | 纯控制面 SaaS 的交付形态 |

参考：<https://buildkite.com/docs>

### 3.10 Dagger

| 维度 | 观察 |
|------|------|
| 定位 | 可编程 CI/CD 引擎（SDK + BuildKit） |
| 配置 | Go/TypeScript/Python SDK；容器化步骤；本地与 CI 同引擎 |
| 优势 | 流水线可测试、可复用；缓存与本地一致性；可挂在任意 CI |
| 短板 | 不是控制面/触发/审批产品；学习曲线；需要容器运行时 |
| 可借鉴 | 可测试的流水线代码、缓存模型、跨 CI 复用 |
| 不照搬 | 作为产品主 DSL 取代可视化编排 |

参考：<https://dagger.io/>

### 3.11 Spinnaker

| 维度 | 观察 |
|------|------|
| 定位 | 多云持续交付平台 |
| 模型 | Application → Pipeline → Stage；集群/负载均衡/安全组管理 |
| 优势 | 多云发布、金丝雀分析（Kayenta）、Pipeline 模板 |
| 短板 | 运维极重；新项目大多转向 Argo CD/Rollouts |
| 可借鉴 | 发布策略抽象、金丝雀分析、应用为中心 |
| 不照搬 | 自建多云抽象层的重运维模式 |

参考：<https://spinnaker.io/docs/>

### 3.12 Backstage / Port / 平台工程（IDP）

| 维度 | 观察 |
|------|------|
| 定位 | 开发者门户与内部平台 |
| 模型 | Software Catalog、Software Templates、TechDocs、插件；Port 用 Blueprints/Actions/Scorecards；Cortex 用 Context Graph/Scorecards；Humanitec 用 Platform Orchestrator；Score 用 workload spec |
| 页面 IA | Catalog → Component → 关系/CI/CD/文档；Create → Template 向导（参数 → Review → Create → Success/Failure，失败可展开步骤日志，可 Start Over） |
| 优势 | 黄金路径、服务所有权、依赖关系、插件生态；把 CI/CD 嵌入服务上下文；Port 的自助 Action 表单/审批；Score 的 workload-centric 抽象 |
| 短板 | 需要平台团队持续运营；插件质量参差；不是 CI 引擎；自托管维护重（Roadie 2025：81% Scaffolder 用户获得时间节省，但 91% 自托管、维护负担是主要痛点） |
| 可借鉴 | 服务目录、模板向导的 Review/Success/Failure、服务详情聚合运行/环境/文档、自助 Action、Scorecards |
| 不照搬 | 一开始就做通用插件市场；把 IDP 当作 CI 引擎 |

参考：<https://backstage.io/docs/>、<https://docs.port.io/>、<https://www.cortex.io/>、<https://humanitec.com/platform-engineering>、<https://docs.score.dev/docs/>

### 3.13 工程效能与研发智能（DX / Faros / LinearB / Swarmia / Cortex）

| 维度 | 观察 |
|------|------|
| 定位 | 以 DORA、SPACE、DX Core 4、价值流、AI impact 为核心的研发效能平台 |
| 页面 IA | 仪表盘 / 团队 / 价值流 / 调查 / 集成；以只读分析为主，部分产品提供 PR 自动化或 working agreements |
| 优势 | DORA 与 AI impact 模型成熟；团队分群与价值流定位；Cortex 把 Context Graph 与 Scorecards 结合 |
| 短板 | 多为只读仪表盘；归因困难、调查疲劳、数据接入成本高；正被“直接在 IDE/对话里问指标”冲击 |
| 关键发现 | Faros 2025 遥测显示 AI 采用后任务 +21%、PR +98%，但 review 时间 +91%、bug 率 +9%，组织级交付指标持平——“AI 生产力悖论” |
| 可借鉴 | DORA + AI impact + 价值流组合；把指标嵌进日常工具，而不是另开 dashboard；团队分群与异常下钻 |
| 不照搬 | 通用 BI 平台；纯只读、与执行脱节的报表 |

参考：<https://dora.dev/>、<https://www.faros.ai/blog/key-takeaways-from-the-dora-report-2025>、<https://www.cortex.io/>

### 3.14 国内一体化平台

| 平台 | 形态 | 可借鉴 | 不照搬 |
|------|------|--------|--------|
| 阿里云云效 Flow | 可视化 + YAML，代码/项目/制品一体 | 低门槛编排、模板、企业权限 | 强绑单一云 |
| 华为云 CodeArts Pipeline | 可视化为主，私有化/信创 | 私有化交付、审批与合规 | 生态封闭、体验偏重 |
| 腾讯云 CNB | 声明式 `.cnb.yml`，事件驱动 | 配置即代码、AI 叙事 | 深度绑腾讯云 |
| 极狐 GitLab | 完整 GitLab CI | 私有化 DevSecOps 深度 | 整套搬 GitLab |
| CODING / 嘉为蓝鲸 | Jenkins 或自研封装 | 内网、主机发布 | 继续堆 Jenkins 插件 |

参考：<https://www.aliyun.com/product/yunxiao>、<https://www.huaweicloud.com/product/codearts.html>、<https://cnb.cool/>

---

## 4. 信息架构对照

### 4.1 导航模型

| 产品 | 一级导航（与交付相关） | 运行入口 | 环境/发布入口 |
|------|------------------------|----------|----------------|
| GitHub Actions | Code / Actions / Security / Insights | 仓库 Actions → Run | Environments |
| GitLab | Build / Deploy / Operate / Monitor | Build → Pipelines | Deploy → Environments |
| Jenkins | Dashboard / Jobs / Build Queue | Job → Build | 无原生环境模型 |
| Tekton | Pipelines / TaskRuns / PipelineRuns | PipelineRuns | 无 |
| Argo | Workflows / Applications / Rollouts | Workflow → Run | Applications / Rollouts |
| Harness | Pipelines / Deployments / Environments / Services | Pipelines → Execution | Environments / Services |
| Azure DevOps | Pipelines / Environments / Releases | Pipelines → Run | Environments |
| CircleCI | Dashboard / Projects / Insights | Pipeline → Workflow | 无原生环境模型 |
| Buildkite | Pipelines / Builds / Agents / Analytics | Builds | 无原生环境模型 |
| Backstage | Catalog / Create / Docs | 服务详情内嵌 | 服务详情内嵌 |

**共性规律**

1. **Run 是一等对象**，有独立列表与详情，而不是只能从流水线进入。
2. **环境（Environment）是 CD 的枢纽**，把部署历史、审批、变量、保护规则收在一起。
3. **服务/组件（Service/Component）是聚合入口**，把代码、流水线、环境、健康度放在一页。
4. **洞察（Insights/Analytics）独立成一级**，而不是散落在各页面。

### 4.2 页面清单模式

| 页面类型 | 必备信息 | 成熟产品做法 | HFWAS 现状 |
|----------|----------|--------------|------------|
| 流水线列表 | 名称、负责人、最近运行、成功率、耗时、标签 | 服务端筛选、收藏、趋势 sparkline | 基础分页 + 当前页筛选 |
| 流水线详情 | 定义、最近运行、版本、设置 | 多页签 + 健康度 | 定义 + 历史 |
| 编辑器 | 可视化 / YAML、模板、校验、预览 | 双模 + 模板 + dry-run | 仅可视化 |
| 运行列表 | 状态、流水线、分支、触发人、耗时 | 全局运行中心 + 保存视图 | 仅流水线内历史 |
| 运行详情 | 阶段/步骤、日志、产物、审批、重试 | 三栏 + 实时日志 + 失败摘要 | 泳道 + 轮询日志 |
| 环境详情 | 部署历史、漂移、回滚、保护规则 | 环境为中心 | 无 |
| 洞察 | DORA、失败率、耗时、Flaky、成本 | 可下钻 | 仅成功率 |

---

## 5. 关键用户旅程对照

### 5.1 创建到首次成功运行

| 步骤 | 成熟产品 | HFWAS 现状 | 差距 |
|------|----------|------------|------|
| 选择起点 | 模板 / Marketplace / 复制 / YAML | 空白或复制（无模板入口） | 高 |
| 配置仓库 | OAuth / App / 连接器 / 凭证 | HTTPS + Token | 中 |
| 编排 | 可视化 + YAML + 矩阵/复用 | 阶段泳道 + 任务卡 | 中 |
| 校验 | 保存前 lint / dry-run / 依赖检查 | 前端结构校验 | 高 |
| 首次运行 | 一键运行 + 实时日志 + 失败建议 | 运行 + 轮询日志 | 中 |
| 结果 | 产物/摘要/通知/回写 | 状态 + 日志 | 高 |

### 5.2 运行与观测

| 能力 | GitHub Actions | GitLab | Harness | HFWAS |
|------|:--------------:|:------:|:-------:|:-----:|
| 全局运行列表 | ✅ | ✅ | ✅ | ❌ |
| 步骤级时间线 | ✅ | ✅ | ✅ | ❌（仅阶段/任务） |
| 实时日志 | ✅ | ✅ | ✅ | ❌（3s 轮询） |
| 日志搜索/下载 | ✅ | ✅ | ✅ | ❌ |
| 失败步骤高亮 | ✅ | ✅ | ✅ | 部分 |
| 重试失败任务 | ✅ | ✅ | ✅ | ❌ |
| 按原参数重跑 | ✅ | ✅ | ✅ | ❌ |
| 运行参数回显 | ✅ | ✅ | ✅ | ❌ |
| 产物/SBOM | ✅ | ✅ | ✅ | 后端有、前端无 |

### 5.3 排障

成熟产品的共同路径：

```text
运行列表/通知 → 运行详情 → 失败摘要 → 失败步骤日志（定位到行）
  → 修复/重试 → 对比上次运行 → 关闭
```

HFWAS 当前路径：

```text
流水线列表 → 运行历史 → 运行详情 → 点任务 → 右侧日志抽屉 → 人工翻找
  → 回编辑器修改 → 重新运行（参数需重填）
```

主要断点：没有失败摘要、没有错误行定位、没有重试、没有运行对比、参数不可复用。

### 5.4 审批与发布

| 能力 | GitHub | GitLab | Harness | Azure DevOps | HFWAS |
|------|:------:|:------:|:-------:|:------------:|:-----:|
| 环境模型 | ✅ | ✅ | ✅ | ✅ | ❌ |
| 审批通过 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 审批驳回/意见 | 部分 | ✅ | ✅ | ✅ | ❌ |
| 多级/多人审批 | 部分 | ✅ | ✅ | ✅ | ❌ |
| 审批超时/转交 | 部分 | ✅ | ✅ | ✅ | ❌ |
| 部署历史 | ✅ | ✅ | ✅ | ✅ | ❌ |
| 回滚 | 部分 | ✅ | ✅ | ✅ | ❌ |
| 漂移检测 | 部分 | 部分 | ✅ | 部分 | ❌ |
| 渐进式发布 | 部分 | 部分 | ✅ | 部分 | ❌ |

### 5.5 治理与分析

| 能力 | 成熟产品 | HFWAS |
|------|----------|-------|
| RBAC | 项目/环境/流水线级 | 以 admin/普通用户为主 |
| 审计 | 全量写操作 | 部分 |
| 策略门禁 | OPA/Kyverno/内置 | 无 |
| SBOM/签名 | 默认能力 | 后端有 SBOM，签名部分 |
| DORA | 内置或集成 | 仅成功率 |
| 成本 | 分钟/资源/租户 | 无 |
| Flaky Test | CircleCI/Buildkite/Harness | 无 |

---

## 6. 值得借鉴的 28 个 UX 模式

| # | 模式 | 代表产品 | 为什么有效 | HFWAS 采用 |
|---|------|----------|------------|------------|
| 1 | Run 作为独立一级对象 | GitHub、GitLab、Harness | 用户按“这次跑得怎么样”而不是“哪条流水线”思考 | H1 |
| 2 | 运行详情三栏：导航 / 时间线 / 上下文 | GitHub、Harness | 减少页面跳转，日志与结构同屏 | H1 |
| 3 | 步骤级状态与耗时 | 全部成熟产品 | 快速定位慢步骤与失败步骤 | H1 |
| 4 | 失败摘要 + 建议动作 | Harness、GitHub | 把“看日志”变成“知道下一步” | H1 |
| 5 | 实时日志 + 搜索/下载/跳转错误行 | Buildkite、Harness | 排障效率的核心 | H1 |
| 6 | 重试失败任务 / rerun failed | GitHub、GitLab | 避免整条重跑，节省时间与成本 | H1 |
| 7 | 按原参数重跑 | GitLab、Harness | 复现问题与发布回滚的刚需 | H1 |
| 8 | 运行参数快照与回显 | 全部 | 审计与复现的基础 | H1 |
| 9 | 流水线模板 / 黄金路径 | Backstage、Harness、GitLab | 把最佳实践固化为默认值 | H1-H2 |
| 10 | 可视化 + YAML 双模 | Azure DevOps、GitLab | 兼顾新手与高级用户 | H1 |
| 11 | 保存前 dry-run / 编译预览 | GitLab、Harness | 降低首次运行失败率 | H1 |
| 12 | 版本化 + diff + 回滚 | GitLab、Harness | 变更可审计、可恢复 | H1 |
| 13 | Environment + 保护规则 | GitHub、GitLab | 把部署风险显式化 | H2 |
| 14 | 审批中心 / 待办 | Harness、Azure DevOps | 审批人不需要追踪每条运行 | H1 |
| 15 | 部署历史 + 一键回滚 | Argo CD、Harness | 发布失败时最短恢复路径 | H2 |
| 16 | GitOps 同步状态与漂移 | Argo CD | 区分“流水线成功”与“集群真实状态” | H2 |
| 17 | 渐进式发布可视化 | Argo Rollouts、Harness | 把发布风险控制在可观察范围 | H2 |
| 18 | 服务目录 / 服务详情聚合 | Backstage、Port | 从“服务”而不是“流水线”组织信息 | H2 |
| 19 | 工程洞察与下钻 | CircleCI Insights、Harness、DX | 从单次运行上升到趋势与改进 | H3 |
| 20 | AI 侧栏：解释、建议、生成 | GitHub Copilot、GitLab Duo、Harness AI | 降低学习成本与排障时间 | H4 |
| 21 | Waterfall：等待 / 分配 / 运行三段着色 | Buildkite | 直接回答“慢在排队还是执行”，定位 Runner 容量问题 | H3 |
| 22 | Pipeline Understanding：自动解释流水线意图 | Harness | Legacy YAML 的“为什么”往往只存在个人脑中 | H3-H4 |
| 23 | 自然语言生成流水线草稿 + diff + 策略校验 | Harness Architect Mode、GitHub Agentic Workflows、GitLab CI Expert Agent | 降低 YAML 门槛，同时保留审查与回滚 | H4 |
| 24 | 工件 / SBOM / provenance 内联展示 | GitHub attestations、Argo Artifact Visualization | 调试与信任在同一上下文，不必跳转外部存储 | H1-H2 |
| 25 | 检查编排化：pre/post/dynamic/exclusive lock | Azure DevOps checks | 合规是组合策略，不是单个“批准”按钮 | H2 |
| 26 | MCP 作为一等集成面 | GitHub、GitLab、Harness、CircleCI、Buildkite、Dagger、Port | Agent 与 IDE 直接调用 CI/CD 能力，形成工具互操作层 | H4 |
| 27 | 运行页内成本 / 碳排 / 缓存节省 | CircleCI、Buildkite、GitLab Carbon Awareness | FinOps 与可持续性需要每次运行级可见性 | H3 |
| 28 | 测试智能：flaky 检测/隔离/选择性重跑 | Buildkite Test Engine、CircleCI、GitLab | Flaky 是 CI 信任度的头号杀手，全量重跑太贵 | H3 |

### 6.1 反面模式（不要学）

| 反面模式 | 问题 | 例子 |
|----------|------|------|
| YAML 作为唯一入口 | 新手门槛高、易错 | Jenkinsfile、纯 Tekton |
| 双编辑器长期并存 | 维护成本、行为不一致 | Azure DevOps 经典 + YAML |
| 无限插件生态 | 安全与质量不可控 | Jenkins |
| 把 CD 写进 CI 脚本 | 无漂移修复、无审计 | 流水线里直接 `kubectl apply` |
| 审批只有“通过” | 无法表达拒绝与责任 | 早期内部平台 |
| 日志纯文本轮询 | 排障慢、无法审计 | 多数自研平台 |
| 一开始做 Marketplace | 供给不足、治理缺失 | 多数失败的自研平台 |

---

## 7. 2026-2031 趋势与影响

| # | 趋势 | 置信度 | 证据 / 信号 | 对流水线产品的影响 | HFWAS 阶段 |
|---|------|:------:|-------------|--------------------|------------|
| 1 | 自托管 / 混合部署长期存在 | 高 | JetBrains 2026：72.8% 主 CI/CD 自托管/自管云，纯 on-prem 30.5% | 私有化、数据面自托管、离线交付是基本盘 | H1 起 |
| 2 | AI 辅助编写与修复成为默认 | 高 | GitHub Agentic Workflows、GitLab Duo、Harness AI | 编辑器需要 AI 侧栏、校验与 diff；运行详情需要失败解释 | H4（H1 预留接口） |
| 3 | AI 主要先在“周边”落地 | 高 | JetBrains 2026：48.8% 使用 AI，仅 8% 在构建步骤内跑 AI step | 优先做解释、诊断、建议，而不是把 AI 塞进每个 step | H3-H4 |
| 4 | 受控 AI Agent 执行低风险运维 | 中高 | Harness AI Verification & Rollback；GitHub safe outputs | 策略、审批、审计、eval 成为 AI 的前置条件 | H4-H5 |
| 5 | MCP 成为工具互操作层 | 高 | GitHub/GitLab/Harness/CircleCI/Buildkite/Dagger/Port 均提供 MCP；28% 组织已用 MCP/agent skills | Agent 与 IDE 直接调用 CI/CD 能力；需要工具级权限与审计 | H4 |
| 6 | Agent 治理 / 评测 / 审计产品化 | 高 | GitLab AI Governance Dashboard、工具级审批策略、AI 审计事件 | AI 写操作必须可追溯、可回滚、可评估 | H4-H5 |
| 7 | 确定性供应链：lockfile + immutable + SLSA + keyless signing | 高 | GitHub workflow lockfiles（2026-10 public preview）、artifact attestations、SLSA v1.1 | SBOM、签名、provenance 成为默认，而非插件 | H1-H2 |
| 8 | 平台工程 / IDP 继续整合 | 高 | Backstage、Port、Cortex、Humanitec/Score；DORA 2025 约 90% 组织使用 IDP | 服务目录、黄金路径、所有权成为入口 | H2 |
| 9 | GitOps + 渐进式发布成为 CD 默认 | 高 | Argo CD/Rollouts、Harness、Flagger | 环境、漂移、回滚、指标门禁 | H2 |
| 10 | 工程智能（DORA/CI 健康度/成本）普及 | 中高 | CircleCI Insights、Harness、DX/Faros；DORA 2025 | 独立洞察页面与下钻 | H3 |
| 11 | 测试智能与 Flaky 治理 | 中高 | Buildkite Test Engine、CircleCI flaky beta | 受影响测试选择、隔离、重跑、owner | H3 |
| 12 | 远程缓存 / 增量构建 / monorepo | 高 | Nx/Turborepo/Bazel/Depot、Dagger 内容寻址缓存 | 缓存、affected build、矩阵 | H2 |
| 13 | 自托管 / 弹性 Runner 与成本优化 | 高 | Buildkite、ARC、ephemeral microVM | Runner 管理、配额、成本视图 | H2-H3 |
| 14 | OpenTelemetry 原生可观测 | 中高 | OTel CI/CD semconv 进入 Release Candidate；Dagger OTel trace | 运行 trace 与日志/指标关联；跨 pipeline/生产关联 | H3 |
| 15 | 多运行时（K8s / 主机 / 边缘 / WASM） | 中 | wsr/Moonlit 等 Wasm 沙箱步骤；限制仍多 | 执行器抽象与能力协商；WASM 先用于轻量 lint/脚本 | H5 |
| 16 | 合规证据自动化 | 中高 | SLSA、SBOM、审计包需求 | 证据链、报告导出、策略命中记录 | H5 |
| 17 | 绿色 CI / 碳排 | 中 | GitLab 2026 pipeline 碳排估算 | 成本与碳排估算、碳感知调度 | H3 |
| 18 | AI 生产力悖论 / 价值流度量 | 高 | Faros 2025：PR +98%、review +91%、bug +9%，组织级交付持平 | 必须测端到端价值流，而不是只看代码产出 | H3 |
| 19 | Progressive delivery 与 feature flag 收敛 | 高 | OpenFeature、Argo Rollouts、Harness | deploy/release 分离；canary + flag + 自动回滚 | H2 |
| 20 | 自然语言成为入口，YAML 仍是事实源 | 中高 | Harness Architect Mode、GitHub Agentic Workflows | NL → 草稿 → diff → 策略校验 → 人工确认 | H4 |

### 7.1 对前端的具体含义

- **实时优先**：SSE/WebSocket 成为基础能力，轮询只作为降级。
- **上下文优先**：AI、日志、产物、审批都在运行详情内完成，不再频繁跳页。
- **可解释优先**：每个自动化动作都能看到“为什么”和“证据”。
- **可访问优先**：键盘操作、屏幕阅读、对比度将成为企业采购的硬指标。
- **性能预算**：万行日志、万条运行、复杂 DAG 必须虚拟化与增量渲染。

---

## 8. 能力矩阵

评分：● 完整；◐ 部分；○ 缺失。

| 能力 | GitHub | GitLab | Jenkins | Tekton | Argo | Harness | HFWAS 现状 | HFWAS 2027 |
|------|:------:|:------:|:-------:|:------:|:----:|:-------:|:----------:|:----------:|
| 可视化编排 | ◐ | ● | ◐ | ○ | ● | ● | ◐ | ● |
| YAML 即代码 | ● | ● | ● | ● | ● | ● | ○ | ● |
| 模板/复用 | ● | ● | ◐ | ◐ | ◐ | ● | ○ | ● |
| 版本化/回滚 | ◐ | ● | ◐ | ○ | ● | ● | ○ | ● |
| 全局运行中心 | ● | ● | ◐ | ◐ | ● | ● | ○ | ● |
| 实时日志 | ● | ● | ● | ◐ | ● | ● | ○ | ● |
| 重试/重跑 | ● | ● | ● | ◐ | ● | ● | ○ | ● |
| 审批/环境 | ● | ● | ○ | ○ | ◐ | ● | ◐ | ● |
| 产物/SBOM | ● | ● | ◐ | ◐ | ● | ● | ◐ | ● |
| GitOps/漂移 | ◐ | ◐ | ○ | ○ | ● | ● | ○ | ● |
| 渐进式发布 | ◐ | ◐ | ○ | ○ | ● | ● | ○ | ◐ |
| 策略/合规 | ● | ● | ○ | ○ | ◐ | ● | ○ | ◐ |
| DORA/洞察 | ◐ | ● | ○ | ○ | ◐ | ● | ○ | ◐ |
| AI 辅助 | ● | ● | ○ | ○ | ○ | ● | ○ | ○ |
| 多租户/私有化 | ○ | ● | ● | ● | ◐ | ● | ● | ● |

---

## 9. 差距结论与产品策略

### 9.1 三个代际差距

1. **交互代际**：从“表单 + 轮询”到“运行中心 + 实时上下文”。
2. **平台代际**：从“单条 CI”到“环境 / 服务 / 策略 / 洞察”的平台。
3. **智能代际**：从“人找问题”到“系统解释、建议、受控自治”。

### 9.2 策略选择

| 选择 | 决策 | 理由 |
|------|------|------|
| 执行引擎 | 继续 Tekton；Argo 作为通用工作流与 GitOps | 不重造 runtime，保持可替换 |
| 编排模型 | 阶段模型为默认；H2 引入自由 DAG | 降低认知成本，兼容 CI 语义 |
| CD | 不自研 GitOps 控制器；集成 Argo CD/Rollouts | 成熟度与生态 |
| 供应链 | 集成 Sigstore/SLSA/Trivy/Dependency-Track | 合规刚需，避免自研密码学 |
| 模板 | 自研模板模型 + 内部模板库 | 与任务市场、PM 闭环结合 |
| AI | 自研编排与体验，模型可替换 | 数据与场景是差异化 |
| 生态 | 先内部插件与模板，后开放 | 治理优先 |

### 9.3 不建议投入的方向

- 自研容器运行时、镜像仓库、Git 托管。
- 通用 BI、通用低代码、通用 ITSM。
- 以 Marketplace 数量为目标的生态扩张。
- 无策略约束的全自动生产变更。

---

## 10. 参考链接

### 产品与官方文档

- GitHub Actions：<https://docs.github.com/en/actions>
- GitHub Actions 可视化图：<https://docs.github.com/en/actions/how-tos/monitor-workflows/use-the-visualization-graph>
- GitHub Environments / Deployments：<https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments>
- GitHub Reusable Workflows：<https://docs.github.com/en/actions/concepts/workflows-and-actions/reusing-workflow-configurations>
- GitHub OIDC：<https://docs.github.com/en/actions/concepts/security/openid-connect>
- GitHub Artifact Attestations：<https://docs.github.com/en/actions/concepts/security/artifact-attestations>
- GitHub Agentic Workflows（2026-02 technical preview）：<https://github.blog/changelog/2026-02-13-github-agentic-workflows-are-now-in-technical-preview/>
- GitHub Actions workflow lockfiles（2026-10 public preview）：<https://github.blog/changelog/2026-10-14-github-actions-workflow-lockfiles-in-public-preview/>
- GitLab CI/CD：<https://docs.gitlab.com/ee/ci/>
- GitLab Pipeline Editor：<https://docs.gitlab.com/ci/pipeline_editor/>
- GitLab DAG：<https://docs.gitlab.com/ee/ci/directed_acyclic_graph/>
- GitLab Environments：<https://docs.gitlab.com/ee/ci/environments/>
- GitLab Merge Trains：<https://docs.gitlab.com/ci/pipelines/merge_trains/>
- GitLab Security Dashboard：<https://docs.gitlab.com/user/application_security/security_dashboard/>
- GitLab Duo Fix Pipeline：<https://docs.gitlab.com/user/duo_agent_platform/flows/foundational_flows/fix_pipeline/>
- Jenkins Blue Ocean（2026-07 弃用说明）：<https://www.jenkins.io/doc/book/blueocean/>
- Jenkins Pipeline Graph View：<https://plugins.jenkins.io/pipeline-graph-view/>
- Tekton：<https://tekton.dev/docs/>
- Tekton Dashboard：<https://tekton.dev/docs/dashboard/>
- Argo Workflows：<https://argoproj.github.io/workflows/>
- Argo Workflows Artifact Visualization：<https://argo-workflows.readthedocs.io/en/latest/artifact-visualization/>
- Argo CD：<https://argo-cd.readthedocs.io/>
- Argo Rollouts：<https://argoproj.github.io/rollouts/>
- Argo Events：<https://argoproj.github.io/argo-events/>
- Harness CI：<https://www.harness.io/products/continuous-integration>
- Harness CD：<https://www.harness.io/products/continuous-delivery>
- Harness AI 公告：<https://www.harness.io/blog/unscripted-2025-announcements>
- Azure DevOps Pipelines：<https://learn.microsoft.com/en-us/azure/devops/pipelines/>
- Azure DevOps Approvals & Checks：<https://learn.microsoft.com/en-us/azure/devops/pipelines/process/approvals>
- CircleCI：<https://circleci.com/docs/>
- CircleCI Insights：<https://circleci.com/docs/insights/>
- Buildkite：<https://buildkite.com/docs>
- Buildkite Waterfall：<https://buildkite.com/docs/pipelines/insights/waterfall>
- Buildkite Dynamic Pipelines：<https://buildkite.com/docs/pipelines/dynamic-pipelines>
- Dagger：<https://dagger.io/>
- Spinnaker：<https://spinnaker.io/docs/>
- Backstage Software Templates：<https://backstage.io/docs/features/software-templates/>
- Backstage Software Catalog：<https://backstage.io/docs/features/software-catalog/>
- Port：<https://docs.port.io/>
- Cortex：<https://www.cortex.io/>
- Humanitec：<https://humanitec.com/platform-engineering>
- Score：<https://docs.score.dev/docs/>

### 标准、框架与趋势

- SLSA：<https://slsa.dev/>（v1.1 levels）
- Sigstore：<https://www.sigstore.dev/>
- OpenTelemetry：<https://opentelemetry.io/docs/>
- OTel CI/CD Semantic Conventions：<https://opentelemetry.io/docs/specs/semconv/cicd/>
- DORA：<https://dora.dev/>
- DORA 2025 Report：<https://dora.dev/research/2025/dora-report/>
- Faros 对 DORA 2025 的解读（AI 生产力悖论）：<https://www.faros.ai/blog/key-takeaways-from-the-dora-report-2025>
- CNCF Platforms White Paper：<https://tag-app-delivery.cncf.io/whitepapers/platforms/>
- Model Context Protocol：<https://modelcontextprotocol.io/>
- OpenFeature：<https://openfeature.dev/>
- JetBrains State of CI/CD 2026：<https://www.jetbrains.com/pages/state-of-cicd2026/>
- GitLab Duo：<https://docs.gitlab.com/user/duo_agent_platform/>
- GitHub Copilot：<https://docs.github.com/en/copilot>

### 本地依据

- [`pipeline-5year-roadmap.md`](./pipeline-5year-roadmap.md)
- [`pipeline-management-ui-prototype.md`](./pipeline-management-ui-prototype.md)
- [`cicd-tech-selection.md`](./cicd-tech-selection.md)
- [`../product/02-features/modules/pipeline.md`](../product/02-features/modules/pipeline.md)
- [`../product/01-overview/product-overview.md`](../product/01-overview/product-overview.md)
