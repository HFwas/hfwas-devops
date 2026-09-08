# Tekton 详解

> 日期：2026-09-07
> 定位：CI/CD 执行引擎调研的展开，配合 [cicd-tech-selection.md](./cicd-tech-selection.md)
> 交互对照：Cursor canvas `tekton-deep-dive.canvas.tsx`

Tekton 是 **Kubernetes 原生的 CI/CD 框架**：流水线是集群里的 Custom Resource，一次运行就是一次调谐。它 **不是** GitHub Actions 那种开箱产品，也 **不是** Jenkins 那种带 UI 的服务器。平台团队用它的积木拼自己的流水线系统；红帽 OpenShift Pipelines 是最完整的企业发行版。

2026-03 从 CDF 迁入 **CNCF Incubating**（此前已在 CDF 毕业）。出身是 Google Knative Build（约 2018）。

---

## 1. 框架 vs 产品

装上 Tekton Pipelines 之后，集群多了 `Task`、`Pipeline`、`TaskRun`、`PipelineRun`。`kubectl` / API 对待它们和对待 Deployment 一样。

| 它解决 | 它不解决 |
|--------|----------|
| 把构建变成可调度的 K8s 工作负载 | 代码托管、项目管理、需求看板 |
| 配额、RBAC、NetworkPolicy、污点直接套在 CI 上 | 云效式可视化编排、租户账单 |
| 可复用 Task、事件触发、构建签名 | GitOps 对账（那是 Argo CD / Flux） |

业务研发几乎不应手写这些 CRD。正确用法是：**自研控制面生成对象，Tekton 只负责跑。**

---

## 2. 组件全家桶

按需安装，不是一个单体。

| 组件 | 职责 |
|------|------|
| **Pipelines** | 核心引擎与 CRD |
| **Triggers** | HTTP webhook → 创建 PipelineRun |
| **Pipelines-as-Code** | 仓库 `.tekton/` + 注解，配置跟代码走 |
| **Chains** | 给运行结果签名，产出 SLSA / in-toto provenance |
| **Catalog + Hub + Resolver** | 社区 Task（git-clone、buildah…）；远程引用，替代已淘汰的 ClusterTask |
| **Operator / Dashboard / `tkn`** | 安装升级、看运行、命令行 |

---

## 3. 对象模型

### 3.1 模板和实例必须分开

| 对象 | 是什么 | 对应 Kubernetes |
|------|--------|-----------------|
| **Step** | 一条命令 + 一个容器镜像 | Pod 里的一个 Container（**不是**独立 CRD） |
| **Task** | 一组有序 Step，共享磁盘 | **一个 Pod 的模板** |
| **Pipeline** | Task 组成的 DAG | 多 Pod 工作流的模板 |
| **TaskRun** | Task 的一次执行 | 真正拉起那个 Pod |
| **PipelineRun** | Pipeline 的一次执行 | 按 DAG 创建多个 TaskRun |
| **Custom Task + Run** | 自定义控制器 | 审批、调外部 API——不一定起 Pod |

换仓库 URL、换 commit SHA，改的是 Run 的 `params`，不是 Task 定义。这是和「把 URL 写死在 Jenkinsfile 里」的关键差别。

### 3.2 一个 Task 里发生什么

- 每个 Step 一个容器镜像：`git`、`maven:3.9-eclipse-temurin-21`、`gcr.io/kaniko-project/executor` 互不污染 `PATH`
- kubelet 会把这些容器一起拉起来；Tekton 注入 **entrypoint**，用完成标记文件做 **串行**：上一步成功，下一步才执行用户命令
- 同 Task 用 **workspace**（常见 `emptyDir`）传源码和 `target/`
- **不是**「每一步一个 Pod」。一步一个 Pod 更接近 Argo Workflows 的默认模型

### 3.3 Pipeline 怎么编排

- 默认无依赖的 Task **并行**（clone 之后 lint / test / scan 同时起三个 Pod）
- `runAfter: [lint, test]` 做汇聚
- `$(tasks.build.results.IMAGE_DIGEST)` 把上一个 Task 的 **小结果** 传给下一个
- `when`（CEL）跳过任务；`matrix` 做版本/架构笛卡尔积；`finally` 无论成败都跑（通知、清理）
- `retries` 针对 flaky 测试

### 3.4 数据：Results vs Workspaces

| | Results | Workspaces |
|---|---------|------------|
| 体积 | 小（digest、布尔、短字符串） | 大（源码树、构建产物） |
| 同 Task | 步骤写文件到 `$(results.X.path)` | emptyDir 立刻可见 |
| 跨 Task | `$(tasks.foo.results.X)` | 必须 PVC（或 Trusted Artifacts） |
| 绑定时机 | Task 声明名字 | **模板只声明名字，Run 才绑卷** |

Workspace 在 PipelineRun 里可以是：

- `volumeClaimTemplate`：这次运行独占一块盘，跑完可回收
- 已有 PVC：Maven 缓存等跨运行共享（要注意脏缓存）
- `emptyDir` / Memory emptyDir：单 Task 暂存
- Secret / ConfigMap：只读凭证和配置

RWO 盘跨 Task 时，Tekton 用 **Affinity Assistant** 尽量把相关 Pod 调度到同一节点。

---

## 4. 一次运行时序

```
Git push
  →（Triggers 或自研 API）创建 PipelineRun
    → 控制器按 DAG 生成 TaskRun
      → 每个 TaskRun = 1 Pod
        → init 准备 /tekton
        → Step 容器排队执行
        → 退出码写回 TaskRun.status
    → 下游 Task 才调度
    →（可选）Chains 签名镜像与 provenance
```

状态、日志都在集群对象上：`tkn pipelinerun logs`、`kubectl describe taskrun`。失败原因经常是「Pod Pending / 卷没挂上 / SA 没权限 / 命令非零」，和业务 YAML 无关的那一层也必须会查。

**调度陷阱：** Kubernetes 把 Pod 内 **所有容器的 request 加总**，尽管 Tekton Step 串行。五个 Step 各 2 CPU，调度器看成 10 CPU，可能一直 Pending。

**安全陷阱：** 不要把宿主机 `docker.sock` 挂进构建 Pod。集群内构建用 **Kaniko 或 Buildah**。

---

## 5. 触发：谁创建 PipelineRun

| 方式 | 机制 | 适合 |
|------|------|------|
| 手动 | `kubectl apply` / `tkn pipeline start` | 调试 |
| **Triggers** | EventListener → Interceptor（验签、过滤）→ TriggerBinding（抽字段）→ TriggerTemplate（stamp Run） | GitHub/GitLab webhook |
| **Pipelines-as-Code** | 仓库 `.tekton/` + `on-event` 注解 | 想要「配置跟代码走」 |
| **自研控制面** | 后端用 K8s API `create PipelineRun` | **多租户产品** |

Triggers 把「解析 webhook」和「Pipeline 长什么样」拆开，这是刻意的。产品若已有 Spring 收 Git 事件，可以直接调 API 创建 Run，**不必第一期就装 Triggers**。

---

## 6. Chains 与供应链

Chains 盯 TaskRun 完成事件，按 in-toto / SLSA 生成 provenance，用 Cosign（可走 Fulcio 无密钥签发）签镜像。用来证明：**这镜像来自这条流水线、这个 commit、这组 Task**，而不是某台无人认领的构建机。企业合规（SLSA L3 口径）是它存在的原因。

---

## 7. 和相邻技术比

| | Tekton | GitHub Actions | Jenkins | Argo Workflows |
|---|--------|----------------|---------|----------------|
| 本质 | CI **框架** | SaaS **产品** | CI 服务器 + 插件 | 通用 DAG 引擎 |
| 执行 | Task=Pod，Step=容器 | Job≈VM | Agent JVM/OS | 步骤常各起 Pod |
| 复用 | Hub Task + Resolver | Marketplace | Shared Library | WorkflowTemplate |
| 供应链 | Chains 一等公民 | attest action | 插件拼 | 非主场 |
| 最合适 | 平台团队做私有 CI | 代码已在 GitHub | 搬不走的存量 | ML / ETL / 超宽扇出 |

Jenkins 阶段 ≈ Tekton Task，Shared Library ≈ Catalog Task，静态 Agent ≈ 动态 Pod。迁移动机通常是「上 K8s 之后不再养 Master」。

Argo Workflows 更能干动态 fan-out 和数据作业；**纯 CI**（clone、测、打镜像、签 digest）Tekton 的积木更贴。CD 仍应是 Argo CD / Flux，Tekton 不要既构建又充当集群真相源。

---

## 8. 优劣势（作产品 runtime）

**优势**

- 厂商中立，CNCF 生态；OpenShift 有企业包
- 与集群治理一体：Quota、NetworkPolicy、租户 Namespace
- Task 可复用；Chains 补上签名
- 无单独 CI 控制面单点（控制器是 K8s Deployment，可多副本）

**劣势**

- **必须有集群**；本地 SQLite 开发体验接不上
- 原生 YAML 啰嗦，不能当用户 DSL
- 跨 Task 传大文件比「一台 Agent 一块盘」麻烦
- Dashboard 偏运维，不是研发门户
- 学习曲线是 Kubernetes 本身

多租户生产常见做法：每团队一个 Namespace + ResourceQuota + LimitRange + 独立 ServiceAccount；PipelineRun 用 `generateName`；PVC 用 `volumeClaimTemplate` 避免打满节点。

---

## 9. 对 HFWAS 流水线

控制面（租户、可视化、PM 回写）自研。执行层把用户 Stage/Job **编译** 成 Pipeline / PipelineRun。

| 产品概念 | Tekton |
|----------|--------|
| 步骤库 | 平台维护的 Task（或 resolver 指向内部 Git） |
| 用户流水线 | Pipeline 或嵌入的 `pipelineSpec` |
| 一次构建 | PipelineRun |
| 日志 / 重跑 | TaskRun 状态 + 再提交 Run |
| Git 事件 | 第一期 Spring 收 webhook 即可 |
| 镜像签名 | 制品仓库上线时接 Chains |

当前仓库「零外部依赖可启动」只覆盖 PM / 接口测试等现有模块。流水线模块 **执行引擎即 Tekton**：控制面编译 Stage/Job 为 PipelineRun，不把 CRD 暴露给业务用户。本地开发用可选的 k3d/kind + Tekton（compose profile），不要再做一层 Docker Agent 双后端。

离线 / 隔离网：官方包已落在 `deploy/tekton/`（Pipelines `v1.15.1` 的 `release.yaml` + 镜像清单）。有网机 `scripts/pack-tekton-offline.sh` 打 tar，离线机 `scripts/load-tekton-offline.sh` 导入后再 apply，不访问 `infra.tekton.dev` / `ghcr.io`。说明见 [deploy/tekton/README.md](../deploy/tekton/README.md)。
