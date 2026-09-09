# CI/CD 业界技术选型调研

> 日期：2026-09-07
> 背景：控制台已规划「流水线」产品（`comingSoon`），需先按层摸清业界框架与技术，再决定自建范围。
> 交互对照：可在 Cursor 中打开 canvas（见对话中的链接），与本文同步。

---

## 一、核心结论

1. **CI/CD 不是一个工具。** 2026 年业界默认拆成五层：触发编排、执行环境、构建系统、缓存、交付。GitHub Actions 和 Argo CD 不在同一层竞争。
2. **SaaS 层已收敛。** GitHub 用户默认 Actions；GitLab 用户默认 GitLab CI；超大 monorepo 用 Buildkite；Jenkins 存量最大，但新系统很少再选。
3. **自建 DevOps 产品通常自研控制面、复用执行引擎。** 用户很少手写 Tekton YAML；OpenShift Pipelines、部分云构建都把它当 runtime。
4. **对本产品：** 执行引擎选定 **Tekton**。控制面自研（可视化 + 产品 YAML → CRD）；不封装 Jenkins，不做 Docker Agent 与 Tekton 长期双后端。本仓库现用的 GitHub Actions 仍只服务平台自己的构建。
5. **第一期不要做完整 CD。** MVP 停在 clone + 构建 + 测试；运行状态与日志只在 HFWAS 控制台。不回写 GitHub Check，不推镜像、不部署。
6. **已拍板：** 开发用 compose profile 嵌 k3d/k3s + 真 Tekton；生产接外部集群。第一期 SCM **只接 GitHub**。对象是租户业务仓库。克隆认证用通用 **账号密码 / Token**（HTTPS）。触发 **仅控制台手动运行**。不做 GitHub App、不回写 Check、无 webhook。

---

## 二、本仓库现状（调研边界）

| 已有 | 含义 |
|------|------|
| 控制台产品 `pipeline`，路径 `/pipeline/overview`，`comingSoon: true` | 产品能力尚未落地 |
| 同批占位：资源编排、制品仓库 | 流水线不应吞掉这两层 |
| `.github/workflows/maven.yml` 等 | **平台自己的** CI，不是给租户用的流水线 |
| `.github/workflows/jenkins-agent.yml` + 多语言 agent 镜像 | 历史上按 Jenkins 执行器在做，技能可复用，内核不必锁定 |
| PM 设计写明 Phase 2+ 与 CI 回写工作项 | 流水线的差异化在「需求 → 构建」贯通，不在再造 GitLab Issue |

---

## 三、五层模型

| 层 | 职责 | 代表技术 | 常见错误 |
|----|------|----------|----------|
| 1. 触发与编排 | Webhook / PR / 定时、DAG、审批、密钥、可视化 | GitHub Actions、GitLab CI、Jenkins、Tekton Pipeline | 把编译图、发布策略全写进 YAML |
| 2. 执行环境 | 真正跑 job 的 VM / 容器 / Pod | Hosted runner、ARC、Buildkite Agent、K8s Job | 没有隔离、没有弹性、密钥进镜像 |
| 3. 构建系统 | 增量编译、测试选择、产物 | Maven/Gradle、Nx、Turborepo、Bazel、Earthly | CI 里裸跑 `npm i && npm test`，无图无缓存 |
| 4. 缓存与远程执行 | 依赖缓存、镜像层、RBE | Actions Cache、Depot、sccache、Nx Cloud | 不命中就每次从零拉依赖 |
| 5. 交付 | 发布、金丝雀、回滚、漂移修复 | Argo CD、Flux、Argo Rollouts、Spinnaker | CI 成功直接 `kubectl apply` 生产 |

分层的收益：一层坏了可以换，不必迁整条交付链。

---

## 四、主流 CI 产品对比

| 产品 | 配置 | 托管 | 优势 | 劣势 | 何时选 |
|------|------|------|------|------|--------|
| **GitHub Actions** | `.github/workflows/*.yml` | SaaS + 自建 runner（ARC） | 与代码同屏；Marketplace 极大；OIDC 换云凭证；可复用 workflow | 绑 GitHub；托管分钟费高；供应链曾出过 marketplace 投毒（需 SHA pin） | 代码已在 GitHub，且不做多 SCM 产品 |
| **GitLab CI** | `.gitlab-ci.yml` | SaaS 或完整自托管 | SCM+CI+SAST/DAST+制品一体；Runner 支持 Docker/K8s/SSH；监管行业私有化强 | 许可证贵；非 GitLab 仓库体验差 | 要一站式 DevSecOps / 私有化 GitLab |
| **Buildkite** | YAML + 运行时动态 `pipeline upload` | 控制面 SaaS，Agent 自建 | 代码不出 VPC；超大并行分片；Shopify 级 monorepo | 要自己运 Agent 池 | 内网构建、超大仓 |
| **Jenkins** | Jenkinsfile / Groovy / 插件 | 100% 自托管 | 插件最多；任意环境；无席位费 | 主节点 SPOF；插件安全与升级债；UI 老化；**新项目很少首选** | 已有无法迁的存量作业 |
| **CircleCI** | `config.yml` + Orb | SaaS 为主 | macOS / ARM / GPU runner、测试分片强 | 通用场景已被 Actions 替代 | iOS、GPU、大测试矩阵 |
| **Azure Pipelines** | YAML / 经典编辑器 | SaaS + 自建 | Azure / .NET / ADO 工作项一体 | 离开微软栈吸引力弱 | Azure 重度 |
| **TeamCity** | Kotlin DSL / UI | 自托管或 Cloud | 构建链、元 runner、JetBrains 栈 | 商业授权；生态小于 Actions | JVM/.NET 企业构建农场 |
| **Bitbucket Pipelines** | YAML | Atlassian Cloud | 和 Jira 回写近 | 绑 Bitbucket Cloud | 已买 Atlassian 全家桶 |

**2026 共识（量级，非本仓库实测）：** JetBrains DevEcosystem 等口径下，Actions 已成为最大默认；Jenkins 实例数仍高，但增长为负；企业新系统常见组合是「Actions 或 GitLab CI 做 CI + Argo CD 做 K8s CD」。

安全侧已成标配，而不是插件：

- 动作用 **commit SHA** 钉死，而不是浮动 tag
- **OIDC** 换云角色，取消长期 AK
- 产物 **Cosign / Notary** 签名 + **SBOM**（SPDX / CycloneDX）+ **SLSA provenance**

---

## 五、适合嵌入产品的执行引擎

自建「流水线」时，控制面（租户、RBAC、可视化、与 PM 回写）应是自己的；执行层建议复用。

| 引擎 | 执行模型 | 优势 | 劣势 | 当产品底座 |
|------|----------|------|------|------------|
| **Tekton** | K8s CRD：Task / Pipeline / PipelineRun | 已进 CNCF（Incubating）；**每个 Task 一个 Pod，每步一个容器**；厂商中立；OpenShift Pipelines 有企业包。展开见 [tekton-intro.md](./tekton-intro.md) | 必须有集群；原生 YAML 啰嗦；本地开发差 | **资源编排上 K8s 之后的首选 runtime** |
| **Argo Workflows** | K8s Workflow DAG | DAG、重试、GPU 调度强；与 Argo CD 同生态 | 更偏批处理 / ML，CI 语义要自己包 | 同时要跑数据或训练作业 |
| **Woodpecker / Drone** | YAML，每步一个容器 | 极轻；多 SCM；自托管成本低 | 插件生态远小于 Jenkins / Actions | 快速可运行、暂不绑 K8s |
| **Gitea / Forgejo Actions** | 兼容 GitHub Actions YAML | 用户会写；`act` 可本地跑 | 兼容不是 100%；不能直接用 GitHub Marketplace | 降低 YAML 学习成本 |
| **Dagger** | Go / TS / Python SDK + BuildKit | 流水线可单测；本地 ≡ CI；可挂在任意 CI 里 | **不替代**控制面与触发 | 高级「可编程步骤」，不是默认 DSL |
| **Earthly** | Earthfile（类 Dockerfile） | 本地/CI 同引擎；BuildKit 缓存 | 仍要外层 CI 触发 | 构建层，不是产品 UI |
| **Jenkins** | Master + Agent + 插件 | 本仓库已有多语言 agent 镜像；国内运维熟 | 控制面重、安全面大、可视化要反向翻译 | **过渡或兼容老客户，不宜做绿野内核** |

补充：

- **Dagger** 定位是「从 YAML 地狱逃到代码」，挂在 Actions / GitLab / Jenkins 里跑，而不是再做一个 SaaS CI。
- **Harness CI**（Drone 商业线）卖点是 AI Test Intelligence：按变更少跑测试。这是后期加速，不是 MVP。

---

## 六、CD / GitOps（与流水线拆开）

| 工具 | 模式 | 优势 | 劣势 |
|------|------|------|------|
| **Argo CD** | Git pull，应用为中心 | K8s 交付事实标准；UI；ApplicationSet 多集群；Rollouts 做金丝雀 | 绑 K8s；VM 发布另做 |
| **Flux + Flagger** | 控制器组合 | CNCF graduated；平台团队可拆 Source / Helm / Image | UI 弱于 Argo |
| **Spinnaker** | 多云推送 | 曾是 Netflix 多云标准 | 运维极重，新项目基本被 Argo 替代 |
| **Octopus** | 环境 / 变量 / 分步发布 | Windows、主机、.NET 细 | 与云原生栈重叠少 |

原则：**CI 产出签名制品并更新 Git 声明；CD 控制器把集群收敛到声明。** 流水线里直接 `kubectl apply` 生产，会失去漂移修复与审计。

主机 / 虚拟机场景仍常见 SSH + Ansible / 自研 Agent，与 GitOps 并存，不要假装全部是 K8s。

---

## 七、国内一体化平台（产品对标）

HFWAS 要对标的是「带流水线的 DevOps 套件」，不是单点 CircleCI。

| 平台 | 流水线形态 | 可借鉴 | 不要学 |
|------|------------|--------|--------|
| 阿里云云效 Flow | 可视化 + YAML | 低门槛编排、和代码/项目一体 | 强绑单一云与部署目标 |
| 华为云 CodeArts Pipeline | 可视化为主 | 私有化 / 信创交付形态 | 生态封闭 |
| 腾讯云 CNB | 声明式 `.cnb.yml`（Pipeline / Stage / Job） | 配置即代码、事件触发、AI 叙事 | 深度绑腾讯云 |
| 极狐 GitLab | 完整 GitLab CI | 私有化 DevSecOps 深度 | 整套搬 GitLab，把 PM 做成弱 Issue |
| CODING / 嘉为蓝鲸 | Jenkins 或自研封装 | 内网、主机发布 | 继续堆 Jenkins 插件当内核 |

竞品文档已写明：GitLab 赢在代码—CI—安全一体，输在工作流精细度。HFWAS 的切口应是 **PM 深度 + 流水线回写**，而不是先做 Marketplace。

---

## 八、构建加速与供应链（第一期留接口即可）

| 类别 | 代表 | 何时认真做 |
|------|------|------------|
| JS monorepo 缓存 | Turborepo / Nx Cloud | 前端仓成为主路径 |
| JVM | Maven 本地仓 + 远程 cache | 后端构建变慢时 |
| 镜像构建 | BuildKit、Depot、Kaniko / buildah（K8s 内） | 开始推镜像 |
| 签名 / SBOM | Cosign、Syft、SLSA attest | 制品仓库上线前 |
| 测试选择 | Harness / 自研 affected | 全量测试超过数十分钟 |

---

## 九、自建产品的三条路径

控制面必须自研（多租户、与现有 Spring Boot / Vue 控制台一体、工作项回写）。分歧只在执行引擎。

### 路径 A — 封装 Jenkins

- **做法：** 产品 UI 调 Jenkins REST / 写 Jenkinsfile；复用现有 agent 镜像。
- **优势：** 最快有「能跑的构建」；国内运维熟悉。
- **劣势：** 主节点与插件安全变成产品债；可视化要翻译 Groovy；和后续 K8s 资源编排拧巴。
- **结论：** 绿野项目 **不推荐当内核**。agent 镜像里的 JDK/Node 工具链可以挪到新 Agent 镜像。

### 路径 B — 自研控制面 + 容器 Agent

- **做法：** 用户侧 YAML（Pipeline / Stage / Job）与可视化双向编辑；执行器先是 Docker（或 compose 旁路），语义保持「一步一容器」。
- **优势：** 不绑 GitHub、不绑某云、不绑集群；本地开发仍可 SQLite；后期同一 YAML 编译成 Tekton PipelineRun。
- **劣势：** 执行语义要自己发明一遍（重试、结果传递、步骤库）；以后切 Tekton 仍是一次编译器迁移。
- **结论：** 仅当第一期 **不能** 有集群时的权宜。引擎已选定 Tekton 后不再作为主路径。

### 路径 C — 控制面直接生成 Tekton（已选定）

- **做法：** 用户流水线（可视化 + 产品 YAML）由控制面编译为 Task / Pipeline / PipelineRun；集群执行。不把 CRD 暴露给业务用户。
- **优势：** 扩展走 Catalog Task、Custom Task、Chains、租户 Namespace，而不是自研插件总线；与后续资源编排同属 K8s。
- **代价：** 流水线能力依赖集群（本地需 k3d/kind 或外接 K8s）；日志、PVC、RBAC 第一期就要设计。
- **不做：** 封装 Jenkins；不做 Docker Agent 与 Tekton 双后端长期并存。

**Dagger：** 可作为「高级步骤：用户提交 TypeScript 流水线」，不是默认用户体验。

---

## 十、已拍板 / 仍待确认

**已拍板**

| 项 | 决定 |
|----|------|
| 执行引擎 | Tekton（控制面编译 CRD，不暴露给用户） |
| 本地执行 | compose profile：k3d/k3s + 真 Tekton |
| 生产执行 | 外接 Kubernetes |
| 第一期 SCM | **仅 GitHub** |
| 服务对象 | 租户业务仓库（平台自身发布仍用 Actions） |
| MVP 范围 | **clone + 构建 + 测试**；日志与状态只在本平台。不回写 GitHub Check、不推镜像、不部署 |
| 克隆认证 | 通用 **用户名+密码** 或 **Token**（HTTPS Basic）。凭证存在平台侧，注入 Tekton Secret，不写进流水线 YAML。GitHub PAT 走 Token 这条。第一期不做 GitHub App、不做 SSH |
| 触发 | **仅控制台手动运行**。无 webhook、无定时 |
| 工具链 | 先选 **技术栈**（Java/Maven、Node、Go、Python），再选该栈的运行时/工具版本，平台映射到镜像。构建/测试 Step 共用该镜像里的二进制（`mvn` / `node` / `go` / `python`），忽略仓库 wrapper（`mvnw`、nvm、pyenv、Go auto-toolchain）。 |
| 编排 UI | **可视化 DAG，不用 Vue Flow。** 采用 GitLab / 云效式「阶段列」：HTML 节点（Naive 卡片）+ SVG 连线，自动分层布局。定义页可增删阶段/任务；运行页同一结构叠状态。 |

范围已收口。设计稿待确认后写入 `docs/superpowers/specs/2026-09-07-pipeline-design.md`。
