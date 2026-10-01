# ============================================================
# Argo Workflows — 使用说明
# ============================================================
# 文件位置: deploy/charts/argo-workflows/samples/
# ============================================================

## 前置条件

Argo Workflows 已经部署好（`./deploy.sh deploy`），Web UI 可用。

## 1. 创建 RBAC（巡检工作流专用权限）

```bash
kubectl apply -f deploy/charts/argo-workflows/rbac/health-inspection-rbac.yaml
```

这会创建：
- ServiceAccount `argo-workflow-health-inspection`
- ClusterRole `argo-workflow-health-inspection`（pods/exec、top、list 等）
- ClusterRoleBinding

## 2. 提交巡检工作流

```bash
# 提交到 argo namespace（Argo Workflows 所在命名空间）
argo submit -n argo \
  -f deploy/charts/argo-workflows/samples/health-inspection-workflowtemplate.yaml \
  -p namespace=devops
```

参数说明：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `namespace` | `devops` | 要巡检的目标命名空间 |
| `report-output` | `true` | 是否将报告输出为 Artifact（需配置 artifact repository） |

## 3. 查看工作流状态

```bash
# 列表
argo list -n argo

# 实时日志
argo logs -n argo <workflow-name> -f

# Web UI
# 打开 http://localhost:32746
```

## 4. 创建 CronWorkflow（定时巡检）

```bash
kubectl apply -n argo -f - <<'EOF'
apiVersion: argoproj.io/v1alpha1
kind: CronWorkflow
metadata:
  name: health-inspection-daily
spec:
  schedule: "0 8 * * *"   # 每天早上 8 点
  timezone: "Asia/Shanghai"
  startingDeadlineSeconds: 0
  concurrencyPolicy: "Replace"
  workflowSpec:
    workflowTemplateRef:
      name: health-inspection
    arguments:
      parameters:
        - name: namespace
          value: devops
EOF
```

## 5. 资源需求

巡检工作流需要以下镜像（已存在于 toolchain-images.txt）：

| 镜像 | 用途 |
|------|------|
| `bitnami/kubectl:1.31.4` | 所有 K8s 操作（list/top/exec） |

如果需要 `kubectl top` 功能，集群需要先安装 **metrics-server**：

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

## 6. 创建自己的 WorkflowTemplate

参考 `health-inspection-workflowtemplate.yaml` 结构：

1. 定义 `entrypoint` 指向主模板
2. 使用 `steps` 或 `dag` 编排步骤
3. 每个 step 指定容器镜像和命令
4. 通过 `{{workflow.parameters.xxx}}` 和 `{{steps.xxx.outputs.parameters.xxx}}` 传参
5. 用 `withParam` 实现并行 fan-out

```yaml
apiVersion: argoproj.io/v1alpha1
kind: WorkflowTemplate
metadata:
  name: my-workflow
spec:
  entrypoint: main
  templates:
    - name: main
      steps:
        - - name: step1
            template: my-task
            arguments:
              parameters:
                - name: msg
                  value: "hello"

    - name: my-task
      inputs:
        parameters:
          - name: msg
      container:
        image: alpine:3.19
        command: [echo]
        args: ["{{inputs.parameters.msg}}"]
```

## 7. 钉钉机器人推送

当告警触发 Workflow 后，自动推送调查摘要到钉钉群。

### 7.1 创建钉钉机器人

1. 钉钉群 → 群设置 → 智能群助手 → 添加机器人 → **自定义**
2. 复制 Webhook URL（格式: `https://oapi.dingtalk.com/robot/send?access_token=xxx`）
3. 如需安全加签，复制加签密钥

### 7.2 创建 Secret

```bash
# 编辑 secret 文件填入真实 Token
vi deploy/charts/argo-workflows/samples/dingtalk-secret.yaml

# apply
kubectl apply -n argo \
  -f deploy/charts/argo-workflows/samples/dingtalk-secret.yaml
```

### 7.3 更新 Sensor 中的 Webhook URL

编辑 `deploy/charts/argo-events/samples/03-sensor-trigger-workflow.yaml`，将 `YOUR_TOKEN_HERE` 替换为真实 Token：

```yaml
- name: dingtalk-webhook
  value: "https://oapi.dingtalk.com/robot/send?access_token=你的真实Token"
- name: dingtalk-secret
  value: "你的加签密钥"
```

### 7.4 手动提交带钉钉推送的 Workflow

```bash
argo submit -n argo \
  -f deploy/charts/argo-workflows/samples/pod-investigation-workflowtemplate.yaml \
  -p namespace=devops \
  -p pod-name=devops-backend-xxxxx \
  -p alert-name=PodRepeatedRestart \
  -p severity=critical \
  -p dingtalk-webhook="https://oapi.dingtalk.com/robot/send?access_token=xxx" \
  -p dingtalk-secret="xxx"
```

### 7.5 推送内容格式

钉钉消息为 **Markdown** 格式，包含：

```
🔴 Pod 故障调查报告 — devops-backend-xxxxx

告警: PodRepeatedRestart
级别: P0 紧急
Pod:  devops-backend-xxxxx
时间: 2026-10-01 14:30:00

调查内容:
◉ 容器退出码与原因
◉ Pod Events 历史
◉ 崩溃前日志（--previous）
◉ 持久化日志检索
◉ Prometheus 资源趋势
◉ Node 状态与事件

⚠️ 该告警为 P0 紧急级别，建议立即处理！

🔗 查看完整报告 → Argo Workflow: investigate-xxxxx
```

### 7.6 安全建议

| 项目 | 建议 |
|------|------|
| Token 存储 | 生产用 External Secrets / SealedSecret |
| Webhook URL | 禁止硬编码在 YAML 中提交 Git |
| 加签 | 推荐开启，防止 Token 泄露后被滥用 |
| @all | 仅 `critical` 级别自动 @所有人 |
```