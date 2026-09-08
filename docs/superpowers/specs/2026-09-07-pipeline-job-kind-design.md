# 流水线任务类型目录

> 日期：2026-09-07  
> 状态：已拍板  
> 父文档：[2026-09-07-pipeline-design.md](./2026-09-07-pipeline-design.md)

对照 GitLab / Jenkins / 云效 / CODING 的任务分类，扩展 `PipelineJobKind`。绿野：直接改枚举、校验、编译与前端类型，不做存量兼容。

原先「占位、保存拒绝」的交付类任务改为**本期可执行**。不新增任务级 JSON 列：镜像由 kind 决定，差异写在命令里（仓库、bucket、webhook、镜像名等）。不新增对象存储 / Registry 凭证表，密钥由用户写在命令或容器环境里。

---

## 1. 目标

- 编辑器列出完整目录，13 种均可保存（校验通过即可）。
- 不新增 ResultCode；参数错误一律 `BizException.of(ResultCode.BAD_REQUEST, 具体原因)`。
- 不新增 `GET /pipeline/job-kinds`。Java 枚举与前端 `JobKind` 对齐。
- `CLONE` 可选（0 或 1）。有 clone 才解析 `repo_url`。
- `APPROVAL` 不是容器：控制面把图画成审批前 / 后两段提交 Tekton。

---

## 2. 目录

枚举元数据：中文名、是否需要用户命令、默认命令（可空）、step 镜像策略。

| kind | 中文 | 需要命令 | 镜像 | 默认命令 / 行为 |
|------|------|----------|------|-----------------|
| `CLONE` | 克隆 | 否 | `alpine/git:2.45.2` | 平台生成 clone；**0 或 1 个**，列不限 |
| `LINT` | 代码检查 | 是 | Semgrep + Sonar Scanner，见 §4.7 | 不跑技术栈镜像。默认 Semgrep；配了 Sonar 地址与 Token 再跑 Sonar |
| `BUILD` | 构建 | 是 | 技术栈镜像 | 默认图用工具链 `buildCommand` |
| `TEST` | 测试 | 是 | 技术栈镜像 | 默认图用工具链 `testCommand` |
| `SCAN` | 安全扫描 | 是 | `aquasec/trivy:0.66.0` | `trivy fs --exit-code 1 --scanners vuln,secret,misconfig .` |
| `PACKAGE` | 打包 | 是 | 技术栈镜像 | 无平台默认 |
| `CUSTOM` | 自定义 | 是 | 技术栈镜像 | 新建任务默认类型；命令如 `echo ok` |
| `IMAGE` | 镜像构建 | 是 | Kaniko + crane + cosign，见 §4.1 | 命令里写 `DEST` / `IMAGE_PLATFORMS`。缓存 PVC、多架构、签名均由平台编译，不挂 docker.sock |
| `PUBLISH` | 发布制品 | 是 | 技术栈镜像 | 无平台默认（`mvn deploy` / `npm publish` 因栈而异） |
| `UPLOAD` | 上传对象存储 | 是 | `rclone/rclone:1.68.2` | 见 §4.2。一个 kind，S3 兼容覆盖阿里云 OSS / 腾讯云 COS / MinIO / AWS S3 |
| `DEPLOY` | 部署 | 是 | `bitnami/kubectl:1.31.4` | `kubectl apply -f k8s/`。目标即**执行流水线的同一集群** |
| `APPROVAL` | 人工卡点 | 否 | 无容器 | 见 §4.3。独占一列 |
| `NOTIFY` | 通知 | 是 | `curlimages/curl:8.11.1` | `curl -fsS -X POST 'https://example.com/hook' -H 'Content-Type: application/json' -d '{"status":"done"}'` |

未知字符串 → `未知任务类型`。

`PUBLISH`：发到 Maven/npm 等包仓库（栈镜像里的客户端）。  
`UPLOAD`：传到对象存储（rclone）。二者不合并。

选成带默认命令的 kind 且当前命令为空时，填入该默认命令（`LINT` / `SCAN` / `IMAGE` / `UPLOAD` / `DEPLOY` / `NOTIFY`）。

---

## 3. 校验

`PipelineGraphValidator`：

1. 至少一列，每列至少一个任务。
2. `CLONE` 至多一个。0 个合法。多于一个 → `流水线至多一个 clone 任务`。
3. `APPROVAL` 所在列只能有这一个任务 → `审批任务必须独占一列`。
4. 同一列至多一个 `IMAGE` → `镜像构建不能与其它镜像构建并行（缓存盘为 RWO）`。
5. 阶段名、任务名非空。
6. `requiresCommand()` 为真时命令非空。
7. `CLONE` / `APPROVAL` 不校验用户命令。

`PipelineDefinitionService.save`：

- 名称不能为空。
- 有 `CLONE` 时 `GitRemote.parse(repoUrl)`；无 clone 时 `repo_url` 可空。

默认图仍是 clone / build / test。clone 可删、可改类型。无 clone 时下拉可出现 `CLONE`；已有则不再出现。

---

## 4. 编译与执行

命令类 step 脚本：

```
set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
<用户命令>
```

`CLONE` / `IMAGE` / `LINT` / `APPROVAL` 不套上面这段「只跑用户命令」的单 step（见各节；APPROVAL 无容器）。无 clone 时命令类任务 `mkdir -p` 保证纯 shell / 扫描 / 上传也能 `cd`。

镜像：`LINT` 见 §4.7；`SCAN` Trivy、`UPLOAD` rclone、`DEPLOY` kubectl、`NOTIFY` curl；`IMAGE` 见 §4.1（多 step）；其余用运行冻结的技术栈镜像。

图中含 `APPROVAL` 时：**不要**把整图打成单 Pod 多 Step（即使每列只有一个任务）。按审批点切段，每段单独提交 TaskRun 或 PipelineRun。

### 4.1 `IMAGE`（Kaniko + 缓存 PVC + 多架构 + 签名）

用户命令**不是**手写完整 Kaniko CLI，而是环境变量（可改）。默认：

```
export DEST=registry.example.com/app:tag
export IMAGE_PLATFORMS=linux/amd64
export DOCKERFILE=Dockerfile
```

每个生成的 IMAGE step 开头先 `eval` 用户命令（导出 `DEST` 等），再跑平台脚本。

#### 缓存 PVC

- 流水线第一次跑到 `IMAGE` 时，若尚无缓存盘则创建 PVC：名 `hfwas-kc-{pipelineId}`（DNS 标签），`10Gi`，`ReadWriteOnce`，命名空间 = `pipeline.namespace`。
- **跨运行保留**，不随 Run 删除。
- Kaniko step 挂载到 `/cache`，固定追加 `--cache=true --cache-dir=/cache`。
- RWO：同一列不能两个 IMAGE（见校验）。IMAGE 与非 IMAGE 同列可以（只有 IMAGE 那个 Task 挂缓存盘）。

#### 多架构

- `IMAGE_PLATFORMS` 逗号分隔，默认 `linux/amd64`。
- **一个** Kaniko step（`gcr.io/kaniko-project/executor:v1.23.2-debug`）内 `for` 循环：每个平台 `--custom-platform=<os/arch> --context=dir://. --dockerfile=$DOCKERFILE --cache=true --cache-dir=/cache`。
- 仅一个平台：`--destination=$DEST`，不加 arch 后缀。
- 多个平台：每架构推 `${DEST}-<arch>`（arch 取 `os/arch` 的 arch 段）。
- 下一 step 固定为 `gcr.io/go-containerregistry/crane:v0.20.3`：多平台时把各 `${DEST}-<arch>` 合成 manifest list 推 `$DEST`；单平台时打印 skip 并以 0 退出。
- 交叉编译（amd64 节点打 `linux/arm64`）要求集群已注册 binfmt（开发 compose privileged 跑一次 `tonistiigi/binfmt --install arm64`）。未注册时失败用 Kaniko/qemu 原文。

#### 镜像签名（Cosign）

- 推送完成后（单平台即 `$DEST`，多平台即 list `$DEST`）加一步 `ghcr.io/sigstore/cosign:v2.4.3`。
- 若 step 环境存在非空 `COSIGN_PRIVATE_KEY`：`cosign sign --yes "$DEST"`（密钥口令 `COSIGN_PASSWORD`，可空）。用户把密钥写在 IMAGE 命令里 `export COSIGN_PRIVATE_KEY=...` 或后续再接凭证表。
- **没有私钥则跳过签名**（step 里打印 `skip cosign: COSIGN_PRIVATE_KEY empty` 并以 0 退出），不把签名当成硬失败。
- 不做 keyless / Fulcio。日志对私钥做与 git 密码相同的打码。

IMAGE 在 Tekton 里固定三个顺序 Step（同一 Task）：Kaniko（含平台循环）→ crane（单平台 skip）→ cosign（无密钥 skip）。不要拆成可并行的 Pipeline Task。

Registry 认证本期由用户/集群准备（Kaniko 读 `/kaniko/.docker/config.json` 若存在）。不新增 Registry 凭证表。

### 4.2 `UPLOAD`（rclone，S3 兼容）

默认命令（用户改 bucket / provider / 密钥）：

```
rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint="${S3_ENDPOINT}" --s3-access-key-id="${S3_ACCESS_KEY}" --s3-secret-access-key="${S3_SECRET_KEY}"
```

| 厂商 | `--s3-provider` |
|------|-----------------|
| MinIO | `Minio` |
| AWS S3 | `AWS` |
| 阿里云 OSS | `Alibaba` |
| 腾讯云 COS | `TencentCOS` |

不拆四个 kind，不加 bucket 表单。密钥走命令或 step env（用户自备），本期不新增凭证 kind。

### 4.3 `APPROVAL`

不是 Tekton Step。

1. 按列顺序把图画成若干段：相邻的非审批列合成一段；每个 `APPROVAL` 列切开。
2. 启动运行：提交第一段。该段成功后：
   - 下一段是审批 → run 状态改为 `WAITING_APPROVAL`，不提交后续；
   - 否则提交下一段。
3. `POST /pipeline/pipelines/{id}/runs/{runId}/approve`：仅当状态为 `WAITING_APPROVAL` 时提交审批后的下一段；否则 `BAD_REQUEST`「当前运行不在待审批」。
4. 取消：`CANCELLED`，已提交的段 best-effort cancel，未提交的段不再提交。
5. 审批失败（用户不点通过、只取消）不自动否决按钮；需要否决则走取消。本期不单独做「拒绝」API。
6. 多个审批：每过一关再进入下一次 `WAITING_APPROVAL`。
7. 前端运行页在 `WAITING_APPROVAL` 显示「通过」。

`pipeline_run.status` 增加 `WAITING_APPROVAL`。

### 4.4 `DEPLOY`

`kubectl` 对着**执行集群**（`pipeline.kubeconfig` / in-cluster）。默认 `kubectl apply -f k8s/`。RBAC = 流水线执行所用账号，本期不单独做部署凭证、不接外部生产集群之外的第二套 kubeconfig。

### 4.5 `NOTIFY`

curl 镜像执行用户命令。Webhook URL 写在命令里。失败则该 step 失败（非 2xx 且 `curl -fsS`）。

### 4.6 `PUBLISH`

与 `BUILD` 相同编译路径（栈镜像 + 用户命令）。只是产品分类不同。

### 4.7 `LINT`（Semgrep + Sonar）

不使用技术栈镜像。一个 `LINT` 任务编译为同一 Task 内 **两个顺序 Step**。每个 step 开头：`mkdir -p src && cd src`，再 `eval` 用户命令，然后跑平台脚本。

默认命令：

```
export LINT_SEMGREP_ARGS="scan --error --config=auto ."
# 要跑 Sonar 时取消注释并填写：
# export SONAR_HOST_URL=https://sonar.example.com
# export SONAR_TOKEN=
# export SONAR_PROJECT_KEY=app
```

| step | 镜像 | 行为 |
|------|------|------|
| Semgrep | `semgrep/semgrep:1.97.0` | `LINT_SKIP_SEMGREP` 非空则 skip 成功；否则 `semgrep $LINT_SEMGREP_ARGS`（参数默认同上） |
| Sonar | `sonarsource/sonar-scanner-cli:11.2` | `SONAR_HOST_URL` 与 `SONAR_TOKEN` 都非空时 `sonar-scanner -Dsonar.host.url=... -Dsonar.token=... -Dsonar.projectKey=${SONAR_PROJECT_KEY:-app} -Dsonar.sources=.`；否则打印 skip 并以 0 退出 |

- 仍是一个 kind，不拆 `LINT_SEMGREP` / `LINT_SONAR`。
- 只跑 Semgrep：默认即可（不配 Sonar 变量）。
- 只跑 Sonar：`export LINT_SKIP_SEMGREP=1` 并填写 Host/Token。
- 两者都跑：填 Sonar 变量且不要 skip Semgrep。
- Token 日志打码，与 git 密码相同。不新增 Sonar 凭证表。
- 不做 SonarQube 服务端安装；Host 由用户自备。Semgrep `--config=auto` 需要出网拉规则，内网失败用 Semgrep 原文。

---

## 5. 前端

- `JobKind` 与上表 13 值一致；`RunStatus` 含 `WAITING_APPROVAL`。
- 类型下拉 13 项，**无「未支持」禁用**。
- `CLONE`：无命令框。`LINT`：命令框提示 Semgrep 参数与可选 Sonar 变量。`IMAGE`：命令框提示填写 `DEST` / `IMAGE_PLATFORMS` / 可选 `COSIGN_PRIVATE_KEY`。`APPROVAL`：无命令框，文案说明运行到此处会暂停，需在运行页点通过。
- 「+ 任务」默认 `CUSTOM`。无 clone 时选项含 `CLONE`。
- clone 可删。无 clone 时仓库地址可空。
- 运行页：`WAITING_APPROVAL` 可点「通过」。
- `refreshDefaultCommands` 仍只刷新仍等于旧默认的 `BUILD` / `TEST`。

---

## 6. 非目标（本期）

- 任务级 JSON / bucket / Dockerfile 表单（IMAGE 只用命令里的 `DEST` / `IMAGE_PLATFORMS` / `DOCKERFILE`）
- 新增 Registry / OSS / 部署 kubeconfig / 通知渠道凭证表
- 按厂商拆 `UPLOAD_*` kind
- Kaniko 缓存盘动态扩容、按租户共享一块盘、cache-repo（仓库缓存）双写
- Cosign keyless / Fulcio / 签名策略强制（无密钥时跳过）
- 自建 Semgrep 规则包托管、SonarQube 服务端
- Argo CD / GitOps、Helm 专用镜像（DEPLOY 只用 kubectl）
- 审批「拒绝」API、审批意见、多人会签、超时自动失败
- 为每种 kind 单独 ResultCode
- GitHub Check、webhook 触发

---

## 7. 验收

1. 默认新建 clone / build / test，可保存、可编译。
2. 只留一个 `CUSTOM`（`echo ok`）、无 `repo_url`，可保存；编译无 git step，有 `mkdir -p src`。
3. `SCAN` step 镜像为 `aquasec/trivy:0.66.0`。`LINT` 编译两个 step：`semgrep/semgrep:1.97.0` 与 `sonarsource/sonar-scanner-cli:11.2`；未配 `SONAR_HOST_URL`/`SONAR_TOKEN` 时 Sonar step skip 成功。
4. `IMAGE` 可保存。同列两个 IMAGE 拒绝。编译三个 step：Kaniko 挂 `hfwas-kc-{pipelineId}` → `/cache` 且 `--cache=true`；crane、cosign 在单平台 / 无密钥时 skip 且成功。
5. `UPLOAD` / `DEPLOY` / `NOTIFY` / `PUBLISH` 可保存；编译使用 §2 对应镜像（`PUBLISH` 用栈镜像）。
6. `APPROVAL` 与其他任务同列时保存失败「审批任务必须独占一列」。独占列可保存。
7. 含审批的流水线：前半段成功后状态 `WAITING_APPROVAL`；点通过后续段才提交；取消后不再提交。
8. 第二个 `CLONE` 保存失败。有 clone 但 URL 非法走 `GitRemote` 错误。
9. 未知 kind → `未知任务类型`。
10. 前端 13 种均可选；无 clone 时可改为 `CLONE`。
