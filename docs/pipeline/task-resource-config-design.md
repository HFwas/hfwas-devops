# Task 资源配置设计方案

> 日期：2026-09-13
> 版本：v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：为任务市场增加 CPU/Memory 资源配置能力 |
| v0.2 | 2026-09-13 | 新增验证层：K8s 格式校验、平台硬限制、集群节点容量感知 warn |

---

## 1. 背景与目标

### 1.1 现状

当前所有 Task 执行时使用集群默认资源配额，不同任务无法差异化配置：

- **CLONE / NOTIFY** 等轻量任务可能被分配到过多资源
- **IMAGE / BUILD / SCAN** 等重型任务可能因资源不足而运行缓慢或被 OOM Kill
- 没有统一的资源配置管理入口

### 1.2 目标

- 在「任务市场」页面为每种 Task 类型单独配置 CPU request/limit 和 Memory request/limit
- 编译 Tekton Manifest 时自动应用到 Step 的 `resources` 字段
- 空值 = 不设置资源约束（使用集群默认值），兼容现有行为

## 2. 数据库设计

### 2.1 新增字段

`pipeline_task_kind` 表追加 4 个 TEXT 列：

```sql
cpu_request    TEXT NOT NULL DEFAULT '',
cpu_limit      TEXT NOT NULL DEFAULT '',
memory_request  TEXT NOT NULL DEFAULT '',
memory_limit   TEXT NOT NULL DEFAULT '',
```

- 使用 TEXT 而非数字类型，因为 Kubernetes 资源量格式多样（`500m`、`1`、`2Gi`）
- 空字符串 = 不设置资源约束（集群默认）
- 默认值为空字符串，现有记录自动兼容

### 2.2 数据流

```
用户 → TaskMarketView 编辑表单 → PUT /pipeline/task-kinds/{kind} → DB
                                                                    ↓
                                                          TektonPipelineExecutor
                                                            读取全部 task kind 的
                                                            cpu_request/limit,
                                                            memory_request/limit
                                                                    ↓
                                                          Map<String, TaskResourceSpec>
                                                                    ↓
                                                          CompileRequest  →  TektonCompiler
                                                                    ↓
                                                          CompiledStep (含 resource 字段)
                                                                    ↓
                                                          TektonManifests → Step.resources
                                                                    ↓
                                                          Kubernetes 调度约束
```

## 3. 后端设计

### 3.1 新增文件

**TaskResourceSpec.java**（pipeline-core/tekton 包）：

```java
public record TaskResourceSpec(
    String cpuRequest, String cpuLimit,
    String memoryRequest, String memoryLimit
) {
    public static final TaskResourceSpec EMPTY = ...;
    public boolean isEmpty() { ... }
}
```

### 3.2 改动文件

| 文件 | 改动 |
|------|------|
| `PipelineTaskKindEntity.java` | 追加 4 个字段 |
| `TaskKindVO.java` / `TaskKindUpdateDTO.java` | 追加 4 个字段 |
| `PipelineTaskKindService.java` | `update()` 和 `toVO()` 追加字段映射 |
| `CompiledStep.java` | record 追加 4 个字符串组件 + compact 构造器适配 |
| `CompileRequest.java` | 追加 `Map<String, TaskResourceSpec> taskResources` |
| `TektonCompiler.java` | `toSteps()` 增加 `taskResources` 参数，新增 `resolveResources()` 方法 |
| `TektonManifests.java` | StepBuilder 链中判断资源值非空时调用 `withNewResources()` |
| `TektonPipelineExecutor.java` | 构建 `taskResources` Map 并传入 CompileRequest |

### 3.3 关键逻辑

资源解析（TektonCompiler）：

```java
private static TaskResourceSpec resolveResources(String kindValue, Map<String, TaskResourceSpec> taskResources) {
    if (taskResources != null) {
        TaskResourceSpec spec = taskResources.get(kindValue);
        if (spec != null && !spec.isEmpty()) {
            return spec;
        }
    }
    return TaskResourceSpec.EMPTY;
}
```

Manifest 生成（TektonManifests）：

```java
// 仅当至少一个有值时设置 resources 块
if (hasCpuRequest || hasCpuLimit || hasMemRequest || hasMemLimit) {
    Map<String, Quantity> requests = new HashMap<>();
    Map<String, Quantity> limits = new HashMap<>();
    if (hasCpuRequest) requests.put("cpu", new Quantity(step.cpuRequest()));
    if (hasMemRequest) requests.put("memory", new Quantity(step.memoryRequest()));
    if (hasCpuLimit) limits.put("cpu", new Quantity(step.cpuLimit()));
    if (hasMemLimit) limits.put("memory", new Quantity(step.memoryLimit()));
    builder.withNewResources().withRequests(requests).withLimits(limits).endResources();
}
```

## 4. 前端设计

### 4.1 TaskKindVO 类型扩展

```typescript
export interface TaskKindVO {
  // ... 现有字段
  cpuRequest?: string
  cpuLimit?: string
  memoryRequest?: string
  memoryLimit?: string
}
```

### 4.2 编辑抽屉新增表单项

在排序字段后追加 4 个输入框：

| 标签 | placeholder | 说明 |
|------|-------------|------|
| CPU 请求 | 500m, 1, 2000m | 留空不限制 |
| CPU 限制 | 1, 2000m, 4 | 留空不限制 |
| 内存请求 | 256Mi, 512Mi, 1Gi | 留空不限制 |
| 内存限制 | 512Mi, 1Gi, 2Gi | 留空不限制 |

使用 `<n-divider />` 与上方常规字段隔开，仅管理员可见。

### 4.3 验证提示

表单项的 placeholder 提示用户输入 K8s 标准资源量格式（如 `500m`、`2Gi`），但不显示硬限制数值（硬限制由后端校验并返回错误消息）。

## 5. 验证层

### 5.1 平台硬限制（application.yml）

```yaml
pipeline:
  task:
    resource-limits:
      max-cpu: 8          # 最大 CPU（核心数），0=不限制
      max-memory: 32Gi    # 最大内存，空=不限制
```

通过 `TaskResourceLimitProperties`（`@ConfigurationProperties`）注入到 `PipelineTaskKindService`。

### 5.2 保存时后端校验流程

```
PUT /pipeline/task-kinds/{kind}
        │
        ▼
PipelineTaskKindService.update()
  ├─ validateResourceQuantity(field, value)
  │   → new Quantity(value) 尝试解析，异常 → BizException("格式错误")
  │
  ├─ validateCpuLimit(field, value)
  │   → 解析 CPU 值与 maxCpu 比较，超限 → BizException
  │
  └─ validateMemoryLimit(field, value)
      → 解析内存值与 maxMemory 比较，超限 → BizException
        │
        ▼
  DB 持久化（仅校验通过）
```

### 5.3 集群感知（运行时）

在 `TektonPipelineExecutor.submit()` 中，构建 `taskResources` 后：

```
1. client.nodes().list() 获取所有 Node
2. 遍历取 max(allocatable.cpu)、max(allocatable.memory)
3. 遍历 taskResources，对比每个 kind 的 cpuLimit/memoryLimit
4. 超过最大节点容量 → log.warn("...Pod 可能无法调度")
5. 不阻止运行，由 K8s 调度器最终决策
```

### 5.4 校验规则

| 校验 | 时机 | 越过操作 | 说明 |
|------|------|----------|------|
| K8s 格式 | 保存时 | 拒绝(400) | 确保值是 `500m`/`2Gi`/`1` 等合法格式 |
| CPU 硬限制 | 保存时 | 拒绝(400) | 超过 `max-cpu` 核数 |
| 内存硬限制 | 保存时 | 拒绝(400) | 超过 `max-memory` |
| 节点容量 | 运行时 | warn 日志 | 超过集群最大节点 allocatable 不阻止 |

## 6. 文件清单

```
新增：
  backend/pipeline-core/.../tekton/TaskResourceSpec.java
  backend/pipeline-core/.../config/TaskResourceLimitProperties.java

修改：
  deploy/charts/backend/files/db/05-pipeline-schema.sql
  backend/server/src/main/resources/db/pipeline-schema.sql
  backend/server/src/main/resources/application.yml
  backend/server/src/main/java/.../DevopsApplication.java            ← @ConfigurationPropertiesScan
  backend/pipeline-core/.../entity/PipelineTaskKindEntity.java
  backend/pipeline-core/.../dto/TaskKindVO.java
  backend/pipeline-core/.../dto/TaskKindUpdateDTO.java
  backend/pipeline-core/.../service/PipelineTaskKindService.java     ← 格式 + 硬限制校验
  backend/pipeline-core/.../tekton/CompiledStep.java
  backend/pipeline-core/.../tekton/CompileRequest.java
  backend/pipeline-core/.../tekton/TektonCompiler.java
  backend/pipeline-core/.../tekton/TektonManifests.java
  backend/pipeline-core/.../executor/TektonPipelineExecutor.java     ← 集群感知 warn
  frontend/src/modules/pipeline/types/pipeline.ts
  frontend/src/modules/pipeline/views/TaskMarketView.vue
  backend/pipeline-core/.../tekton/TektonCompilerTest.java
```

## 7. 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 字段类型 | TEXT | K8s 资源量格式多样，非纯数字 |
| 空值语义 | 不设置 resources 块 | 兼容现有集群默认调度 |
| 限制/请求耦合 | 各自独立 | 用户可能只想配 limit |
| Per-step 粒度 | 同任务内所有 step 共享同一规格 | IMAGE 含 buildah + cosign 两步，资源需求相同 |
| 数据传递方式 | Map<String, TaskResourceSpec> | 沿用 taskImages/taskScripts 既有模式 |