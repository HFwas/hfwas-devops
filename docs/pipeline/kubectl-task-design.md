# KUBECTL 任务类型设计

> 日期：2026-09-13
> 版本：v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：kubeconfig 凭证 + kubectl 命令执行 |

---

## 1. 背景

流水线现有 `DEPLOY` 任务（直接跑 `kubectl apply`），但缺少**独立的 k8s 通用命令任务**，也无法挂载用户提供的 kubeconfig。需要一种新任务类型，能：

1. 使用 `KUBECONFIG` 凭证（存储 kubeconfig YAML）
2. 在 Pod 内创建 `/root/.kube/config`
3. 执行用户输入的任意 kubectl 命令

## 2. 设计

新增 `KUBECTL` 任务种类，复用已有的 credential 模型，扩展支持 `KUBECONFIG` 类型。

### 2.1 数据流

```
用户创建凭证 (kind=KUBECONFIG)
    → 加密存入 pipeline_credential.secret_enc
    → 流水线引用 credential_id
    → 执行时 decrypt → 创建 k8s Secret
    → Tekton Task 以 Volume mount 到 /root/.kube/config
    → command_template 执行用户命令
```

### 2.2 新增 KUBECTL 任务市场条目

在 `05-pipeline-schema.sql` 追加：

| 字段 | 值 |
|------|-----|
| kind_value | `KUBECTL` |
| label | `K8s 命令` |
| task_group | `部署` |
| description | 使用用户提供的 kubeconfig 执行 kubectl 命令 |
| hint | 填写任意 kubectl 命令，如 `kubectl get pods -A` |
| requires_command | 1 |
| tool_image / default_image | `bitnami/kubectl:1.31.4` |
| sort_order | 95（在 DEPLOY 90 之后） |

command_template：

```bash
set -eu
mkdir -p /root/.kube
if [ -f /etc/kubeconfig/config ]; then
  cp /etc/kubeconfig/config /root/.kube/config
fi
cd "$(workspaces.source.path)/src"
${COMMAND}
```

## 3. 修改清单

### 3.1 PipelineJobKind.java

`DEPENDENCY_ANALYSIS` 后追加 `KUBECTL`。

### 3.2 05-pipeline-schema.sql

- 追加 `KUBECTL` 的 INSERT 行
- 追加 `KUBECTL` 的 UPDATE command_template

### 3.3 CompiledStep.java

新增 `usesKubeconfig` 字段（默认 `false`），对齐已有 `usesGitSecret` 模式。

### 3.4 TektonCompiler.java

在 `toSteps()` 通用分支前添加 `KUBECTL` 分支：

```java
if (job.kind() == PipelineJobKind.KUBECTL) {
    String script = resolveScript("KUBECTL", request.taskScripts(), command);
    String image = resolveImage("KUBECTL", request.taskImages(), DEPLOY_IMAGE);
    return List.of(new CompiledStep(base, image, script, env, false, false, true));
}
```

### 3.5 TektonManifests.java

- 新增 `kubeconfigSecret()` 方法创建 kubeconfig Secret
- 修改 `task()` 方法：当 step 需要 kubeconfig 时添加 Volume mount

### 3.6 TektonPipelineExecutor.java

- `submit()` 中：加载 credential 时判断 `kind` 是否为 `KUBECONFIG`，若是则创建 kubeconfig Secret 并注入 Volume
- 日志 mask 中：扫描 kubeconfig Secret 内容

### 3.7 PipelineCredentialService.java

`save()` 中允许的 kind 增加 `KUBECONFIG`（当前只允许 `PASSWORD` / `TOKEN`）。

## 4. 不变的文件

- `CredentialSaveDTO.java` — 字段不变，`username` 可空（kubeconfig 不需要）
- `PipelineCredentialEntity.java` — 实体不变
- `PipelineEntity.java` / `PipelineVO.java` — 无变更
- 前端 — 任务市场自动渲染新 type，无需前端改动

## 5. 验证方式

1. **数据库 migration**：重启后端后检查 `pipeline_task_kind` 表中有 `KUBECTL` 记录，command_template 正确
2. **UI 任务市场**：可看到「K8s 命令」卡片，选择后可输入 kubectl 命令
3. **凭证**：新建 `kind=KUBECONFIG` 的凭证，填入 kubeconfig YAML
4. **流水线执行**：关联 KUBECONFIG 凭证，添加 K8s 命令任务，运行后验证 `/root/.kube/config` 存在且命令执行成功
5. **单元测试**：`TektonCompilerTest` 验证 KUBECTL 生成正确的 Step