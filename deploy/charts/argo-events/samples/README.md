# ============================================================
# Argo Events — Prometheus 告警驱动工作流最佳实践
# ============================================================
# 体系架构:
#
#   Prometheus (发现异常)
#     │ alert threshold triggered
#     ▼
#   AlertManager （分组/去重/静默）
#     │ webhook_configs → POST /alert
#     ▼
#   Argo Events EventSource（webhook 接收端）
#     │ JetStream EventBus
#     ▼
#   Argo Events Sensor（判断告警类型，映射参数）
#     │ trigger: create Workflow
#     ▼
#   Argo Workflows（巡检/修复）
#     │ 自动执行 retryStrategy
#     ▼
#   完成
#
# ============================================================
# 使用步骤
# ============================================================

## 第 1 步：部署 Argo Events

```bash
cd deploy/charts/argo-events
./deploy.sh pull
./deploy.sh deploy
```

## 第 2 步：创建 EventBus

```bash
kubectl apply -n argo-events -f samples/01-eventbus.yaml

# 等待 EventBus Ready
kubectl -n argo-events get eventbus default -w
```

## 第 3 步：创建 EventSource（Webhook 接收端）

```bash
kubectl apply -n argo-events -f samples/02-eventsource-webhook.yaml

# 暴露 NodePort（让 AlertManager 能访问）
kubectl apply -n argo-events -f samples/05-eventsource-service.yaml

# 确认 EventSource Ready
kubectl -n argo-events get eventsources
```

## 第 4 步：创建 Sensor（告警 → Workflow 映射）

```bash
# 先确保已有巡检 WorkflowTemplate
kubectl apply -n argo -f ../argo-workflows/rbac/health-inspection-rbac.yaml
kubectl apply -n argo -f ../argo-workflows/samples/health-inspection-workflowtemplate.yaml

# 创建 Sensor
kubectl apply -n argo-events -f samples/03-sensor-trigger-workflow.yaml

# 确认 Sensor Ready
kubectl -n argo-events get sensors
```

## 第 5 步：创建 Prometheus 告警规则

```bash
kubectl apply -f samples/04-prometheus-rule.yaml
```

## 第 6 步：配置 AlertManager → EventSource

```yaml
# deploy/monitoring/prometheus-values.yaml 中启用 AlertManager
alertmanager:
  enabled: true
  config:
    global:
      resolve_timeout: 5m
    route:
      group_by: ['namespace', 'severity']
      group_wait: 10s
      group_interval: 5m
      repeat_interval: 4h
      receiver: 'argo-events-webhook'
    receivers:
      - name: 'argo-events-webhook'
        webhook_configs:
          - url: 'http://alertmanager-webhook-nodeport.argo-events.svc.cluster.local:12000/alert'
            send_resolved: true
```

```bash
helm upgrade prometheus prometheus-community/kube-prometheus-stack -n monitoring \
  -f deploy/monitoring/prometheus-values.yaml
```

## 第 7 步：验证

```bash
# 手动触发一条告警测试（模拟 AlertManager webhook）
curl -X POST http://localhost:31200/alert \
  -H 'Content-Type: application/json' \
  -d '{
    "version":"4",
    "groupKey":"test",
    "status":"firing",
    "alerts":[{
      "labels":{
        "alertname":"PodRepeatedRestart",
        "severity":"warning",
        "namespace":"devops",
        "pod":"devops-backend-xxxxx",
        "container":"app"
      },
      "annotations":{"summary":"测试告警"},
      "startsAt":"2026-10-01T00:00:00Z"
    }]
  }'

# 查看 Sensor 日志
kubectl -n argo-events logs -l app.kubernetes.io/name=argo-events-sensor -f

# 查看工作流是否被触发
argo list -n argo
```

# ============================================================
# 组件说明
# ============================================================

| 组件 | 命名空间 | 功能 |
|------|---------|------|
| Argo Events Controller | `argo-events` | 管理 EventSource/Sensor CRD |
| NATS JetStream | `argo-events` | 消息总线，EventSource → Sensor |
| EventSource | `argo-events` | Webhook 接收端，接收 AlertManager POST |
| Sensor | `argo-events` | 判断告警类型 → 创建巡检 Workflow |
| EventBus | `argo-events` | 消息通道定义 |
| AlertManager | `monitoring` | 告警路由 → webhook 推送 |
| Argo Workflows | `argo` | 执行巡检/修复工作流 |

# ============================================================
# 扩展：告警类型 → 工作流映射表
# ============================================================

| 告警 | Severity | 触发的工作流 |
|------|----------|-------------|
| PodRepeatedRestart | warning | 巡检: 检查重启原因、日志、资源 |
| KubePodCrashLooping | critical | 巡检 + 堆外内存（Java） |
| NodeNotReady | critical | 巡检: 节点上所有 Pod 状态 |
| NodeMemoryPressure | warning | 巡检: 各 Pod 内存使用分布 |
| NodeCpuHigh | warning | 巡检: 各 Pod CPU 使用分布 |
| JVMOutOfMemory | critical | 巡检: 堆外内存分析 + jcmd |
| KubePodNotReady | warning | 巡检: 检查 Pending/Unknown 原因 |

# ============================================================
# 扩展：接入更多事件源（参考）
# ============================================================

# Argo Events 支持 20+ 事件源，EventSource 只需改 kind:
#   - webhook（AlertManager / GitHub / GitLab）
#   - s3 / minio（文件上传）
#   - kafka / pulsar（消息队列）
#   - cron（定时触发）
#   - calendar（日历触发）
#   - github / gitlab（代码事件）
#   - amqp / mqtt / redis / nats
#   - aws-sns / aws-sqs
#   - stripe / slack / emq
#   - bitbucket / gerrit / jira