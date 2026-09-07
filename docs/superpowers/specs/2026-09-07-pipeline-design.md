# 流水线产品第一期设计

> 日期：2026-09-07  
> 状态：已拍板，进入实施  
> 选型：[cicd-tech-selection.md](../../cicd-tech-selection.md)  
> Tekton 说明：[tekton-intro.md](../../tekton-intro.md)

绿野项目：直接按本文结构建表与 API，不做存量兼容。

---

## 1. 目标

控制台产品「流水线」可配置租户业务 GitHub 仓库，在控制台手动触发 **clone → 构建 → 测试**。执行引擎是 **Tekton**（开发用 compose profile 内嵌 k3d/k3s，生产外接集群）。运行状态与日志只在本平台。

### 1.1 已拍板

| 项 | 决定 |
|----|------|
| 执行引擎 | Tekton。控制面编译 CRD，用户不写、不看 Tekton YAML |
| 本地执行 | compose profile：k3d/k3s + 真 Tekton（Task=Pod、Step=容器） |
| 生产执行 | 外接 Kubernetes |
| SCM | 第一期只接 GitHub HTTPS URL |
| 对象 | 租户业务仓库；本平台自身发布仍用 GitHub Actions |
| MVP 范围 | clone + 构建 + 测试。不推镜像、不部署、不回写 GitHub Check、无 webhook、无定时 |
| 触发 | 仅控制台「运行」 |
| 克隆认证 | 用户名+密码 或 Token（HTTPS Basic）。GitHub PAT 走 Token。不做 GitHub App、不做 SSH |
| 工具链 | 先选技术栈（Java/Maven、Node、Go、Python），再选版本，映射到镜像。用镜像内二进制，忽略 `mvnw` / nvm / pyenv / Go auto-toolchain |
| 编排 UI | 阶段列式 DAG：HTML 卡片 + SVG 连线。**不用 Vue Flow** |

### 1.2 非目标（第一期）

- GitLab / Gitea、GitHub App、SSH、webhook、Check Run
- 推镜像、GitOps、Argo CD、制品仓库、对象存储上传的真实执行（kind 可出现在目录，保存拒绝，见任务类型目录 spec）
- 自定义镜像名、Gradle、Poetry/uv、每任务不同技术栈
- 跨列跳步、节点级取消、YAML 编辑器
- 封装 Jenkins、Docker Agent 双后端

---

## 2. 架构

```
控制台「运行」
  → pipeline-core 落库 pipeline_run
  → 凭证解密写入 K8s Secret
  → 按 DAG 编译并提交 TaskRun 或 PipelineRun
  → watch 状态/日志写回本库
  → 控制台轮询刷新
```

| 层 | 做法 |
|----|------|
| 产品 | 去掉 `comingSoon`，路由 `/pipeline/pipelines` |
| 后端 | 新模块 `pipeline-core`，包名 `com.hfwas.devops.pipeline`，经 `server` 入 classpath |
| 权威数据 | 定义、凭证、Run 历史在 SQLite。集群对象可删可重建 |
| 多租户 | `X-Tenant-Id` + `CurrentUserAccessor`，与 PM / 图片处理一致 |

串行图（每列一个任务）→ **一个 Task / 一个 Pod**，每任务一个容器，共享 workspace。  
某列多个任务 → **Pipeline + 多个 TaskRun**，源码经 PVC 传递。

---

## 3. 工具链

平台维护矩阵，UI 只列出合法组合。流水线存版本号，**不存镜像名**。每次运行冻结 `stack`、`runtime_version`、`tool_version`、`image`。

| stack | runtime_version | tool_version | 镜像 | 默认构建 | 默认测试 |
|-------|-----------------|--------------|------|----------|----------|
| `JAVA_MAVEN` | `17` / `21` | Maven `3.8` / `3.9` | `maven:3.8.8-eclipse-temurin-17` 等 | `mvn -B -DskipTests package` | `mvn -B test` |
| `NODE` | `20` / `22` | `NPM` / `PNPM` / `YARN` | `node:22-bookworm` | 见下 | 见下 |
| `GO` | `1.22` / `1.23` | 空 | `golang:1.23` | `go build ./...` | `go test ./...` |
| `PYTHON` | `3.11` / `3.12` | 空 | `python:3.12-bookworm` | `pip install -r requirements.txt` | `pytest` |

Node 默认命令：

- NPM：`npm ci` / `npm test`
- PNPM：`corepack enable && pnpm i --frozen-lockfile` / `pnpm test`
- YARN：`corepack enable && yarn install --frozen-lockfile` / `yarn test`

Java 发行版钉死 Eclipse Temurin。`GOTOOLCHAIN=local`。clone 步骤镜像固定 `alpine/git:2.45.2`（或等价）。

非法组合（矩阵没有）拒绝保存与运行。

---

## 4. 数据模型

### 4.1 `pipeline_credential`

| 列 | 说明 |
|----|------|
| id, tenant_id, name | |
| kind | `PASSWORD` / `TOKEN` |
| username | TOKEN 默认 `x-access-token` |
| secret_enc | AES 加密，API 永不回传 |
| deleted, create_by, update_by, create_time, update_time | |

### 4.2 `pipeline`

| 列 | 说明 |
|----|------|
| id, tenant_id, name | |
| repo_url | 有 `CLONE` 时必填（http(s) Git URL）；无 clone 时可空 |
| git_ref | 分支或 SHA，默认 `main` |
| credential_id | 可空 |
| stack, runtime_version, tool_version | |
| deleted, 审计列 | |

### 4.3 `pipeline_stage` / `pipeline_job`

阶段是列，任务是列内卡片。依赖 = 列顺序；同列并行。

| `pipeline_stage` | id, pipeline_id, name, sort_order |
| `pipeline_job` | id, pipeline_id, stage_id, name, kind（见 [任务类型目录](./2026-09-07-pipeline-job-kind-design.md)）, command, sort_order |

约束：

- `CLONE` 可选，**至多一个**，列位置不限。允许 0 个 clone（只跑用户命令，例如一个 `CUSTOM` shell）。
- 有 `CLONE` 时：命令由平台生成；`repo_url` 必填且可解析。无 clone 时 `repo_url` 可空。
- 新建流水线默认三列：clone / build / test；clone **可删**。build/test 命令取栈默认值

不存 `layoutX/Y`。

### 4.4 `pipeline_run` / `pipeline_run_job`

| `pipeline_run` | pipeline_id, status (`QUEUED`/`RUNNING`/`SUCCEEDED`/`FAILED`/`CANCELLED`), trigger=`MANUAL`, git_ref, commit_sha, stack, runtime_version, tool_version, image, tekton_name, error_message, started_at, finished_at |
| `pipeline_run_job` | run_id, job_id, stage_name, job_name, kind, command, status, log_text, started_at, finished_at |

日志第一期截断写入 `log_text`（上限 512KiB/任务）。

---

## 5. API

前缀 `/pipeline`（前端 axios `baseURL=/api` → `/api/pipeline/...`）。均需登录。按 tenant 隔离。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/toolchains` | 矩阵 + 默认命令 |
| GET/POST | `/pipeline/credentials`、`/pipeline/credentials/{id}` | 列表/创建；更新可只改 name；删除若被引用则 400 |
| GET | `/pipeline/credentials/{id}` | 无密钥，仅 meta |
| POST | `/pipeline/pipelines/page` | `{ pageNo, pageSize, keyword }` |
| POST | `/pipeline/pipelines` | 创建（可带 stages；缺省则默认三阶段） |
| GET/PUT/DELETE | `/pipeline/pipelines/{id}` | 详情含 stages/jobs |
| POST | `/pipeline/pipelines/{id}/runs` | 手动运行 |
| GET | `/pipeline/pipelines/{id}/runs/{runId}` | 含 jobs + 日志 |
| POST | `/pipeline/pipelines/{id}/runs/{runId}/cancel` | 取消 |

凭证创建 body：`{ name, kind, username, secret }`。`kind=TOKEN` 且 username 空则存 `x-access-token`。

运行：无执行集群配置时返回明确错误，Run 记 `FAILED`（或保持可重试的 `QUEUED` 并在 msg 说明）。第一期：**未配置集群则运行失败并写 error_message**，定义仍可保存。

---

## 6. 前端

### 6.1 路由与 Tab

- 产品 path：`/pipeline/pipelines`，去掉 `comingSoon`
- `/pipeline/pipelines` 列表
- `/pipeline/pipelines/new`、`/pipeline/pipelines/:id` 编辑
- `/pipeline/pipelines/:id/runs/:runId` 运行
- `/pipeline/credentials` 凭证
- 顶栏二级 Tab：流水线、凭证（只在 `/pipeline` 下命中才显示）

模块：`frontend/src/modules/pipeline/`。

### 6.2 列表

页头「流水线」+「新建」。表格：名称、技术栈摘要、仓库短名、最近运行状态/时间、操作（运行、编辑、删除）。

### 6.3 编辑

上：名称、repo_url、git_ref、凭证下拉、技术栈与版本（级联）。换栈且命令仍是旧栈默认时，刷新 build/test 默认命令。

下：**阶段列 DAG**（自研，不用 Vue Flow）

- 列头阶段名，列内任务卡片
- 「+ 阶段」「+ 任务」
- 卡片菜单：改名、改命令、删除（clone 也可删）
- 列间 SVG 连线（前列全部 → 后列全部，视觉上即可）
- 保存写 stages/jobs

页脚说明：使用镜像内工具，不跑 wrapper。

### 6.4 运行页

同一阶段列结构，卡片状态色。点卡片右侧日志。顶栏：运行编号、总状态、冻结的栈与镜像、取消。轮询 2s。

### 6.5 凭证

表格：名称、类型、用户名、时间。密钥 `••••`。新建弹窗。

---

## 7. Tekton 编译

输入：pipeline + stages/jobs + 解析出的 image + clone URL/凭证。

1. 拓扑：阶段升序；同列 jobs 并行。
2. 全图每列恰好 1 个 job → 单个 `Task`/`TaskRun`：steps 按列顺序；有 `CLONE` 则该步用 git 镜像，其余用栈镜像。无 clone 则全部用栈镜像。workspace `emptyDir`。
3. 否则 `Pipeline`：每列一个并行组。有 clone 时 clone 所在列为 git Task。共享 PVC workspace。
4. 仅当存在 `CLONE` 时执行 git clone（`{scheme}://{user}:{secret}@host/path`），日志与 args 中打码。
5. 对象名：`hfwas-{runId}` 截断符合 DNS 标签。

开发 compose profile `pipeline`：k3s/k3d + 安装 Tekton Pipelines。`application.yml`：`pipeline.kubeconfig` 或 in-cluster。

---

## 8. 安全

- 密钥 AES（配置 `pipeline.credential-key`，本地默认开发密钥）
- 列表/详情 VO 无 `secret` / `secretEnc`
- clone URL 日志打码
- 租户隔离：查改删均校验 `tenant_id`
- 不把 docker.sock 挂进构建 Pod；第一期不打镜像故无 Kaniko

---

## 9. 验收

1. 产品目录可进入流水线；凭证 CRUD。
2. 新建默认 clone/build/test 三列；Java 21 + Maven 3.9 保存后再打开一致。
3. 可加并行列内任务；保存后再打开列与卡片一致。
4. 工具链接口只返回矩阵内组合。
5. 无集群时点运行：失败原因可读，定义不丢。
6. 有 Tekton 时：串行三步在同一 Pod 三个容器中执行（可用 `kubectl` 验证）。
7. 控制台能看步骤日志；GitHub 上无 Check。
8. 不引入 Vue Flow 到 pipeline 模块。
