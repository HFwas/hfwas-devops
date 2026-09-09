# 流水线 Pod 终端调试功能 — 设计与实现方案

> 日期：2026-09-09  
> 状态：待实施  
> 关联： [2026-09-07-pipeline-design.md](../superpowers/specs/2026-09-07-pipeline-design.md)

---

## 1. 背景与目标

### 1.1 需求

用户在流水线执行过程中，需要直接点击任务卡片上的「终端」按钮，在浏览器中打开一个交互式 Shell，**直接进入该任务对应的 K8s Pod 内部执行命令、查看现场** — 即 `kubectl exec -it` 的 Web 界面等价。

### 1.2 现状

- **TektonPipelineExecutor** 已通过 fabric8 Kubernetes Client 跟踪 Pod（从 `TaskRun.getStatus().getPodName()` 获取 pod 名），但 Pod 信息仅存留在 watch 线程内存中，未持久化、未通过 API 暴露
- **前端 PipelineRunView** 展示任务卡片（YunxioFlowCanvas），已有「查看日志」按钮和右侧日志抽屉，但没有交互式终端入口
- 前后端均无 WebSocket 基础设施
- 前端无 xterm.js 终端组件

---

## 2. 整体架构

```
浏览器 xterm.js
    │ WebSocket (JWT)
    ▼
Spring WebSocket Handler
    ├─ 容器 Running? → fabric8 ExecWatch → K8s exec → 目标容器
    └─ 容器 Terminated? → fabric8 创建 Debug Pod → K8s exec → debug 容器
```

### 2.1 核心流程（含失败降级）

1. 用户在 PipelineRunView 点击任务卡片的「终端」按钮（**RUNNING / FAILED 状态均可**）
2. 前端打开 PodTerminalDrawer，调用 `GET .../containers` 获取容器列表 + 各容器状态
3. 用户在 selecting 界面选择容器
4. 前端通过 WebSocket 连接后端，URI 含 `{containerName}`
5. 后端查询目标容器状态：
   - **Running**: 直接 fabric8 exec 到该容器（正常流程）
   - **Terminated**（已退出，含失败或成功退出）：创建 Debug Pod（复制原 Pod 的挂载卷 + 添加 busybox debug 容器），然后 exec 到 debug 容器
6. 后端双向桥接：浏览器键盘输入 → K8s Pod Shell；Pod 输出 → xterm.js 渲染
7. 用户关闭抽屉 → 清理 exec 连接；若创建了 Debug Pod → 自动删除

---

## 3. 后端改动

### 3.1 暴露 KubernetesClient 为 Spring Bean

**文件**: `pipeline-core/.../config/PipelineExecutorConfiguration.java`

将 `KubernetesClient` 实例提升为独立的 `@Bean`，使其可被其他组件（如 Pod Exec Handler）注入，而不局限于 `TektonPipelineExecutor`。

```java
@Bean
@ConditionalOnMissingBean
public KubernetesClient kubernetesClient(
        @Value("${pipeline.kubeconfig:}") String kubeconfig
) {
    Path path = StringUtils.hasText(kubeconfig) ? Path.of(kubeconfig) : null;
    if (path == null || !Files.isRegularFile(path)) return null;
    Config config = Config.fromKubeconfig(Files.readString(path));
    return new KubernetesClientBuilder().withConfig(config).build();
}
```

`TektonPipelineExecutor` 改为通过构造器注入这个 bean。

### 3.2 持久化 Pod / 容器信息（支持多容器）

**背景**: 目前 `PipelineRunJobVO` / `PipelineRunJobEntity` 不包含 `podName` / `namespace` / 容器列表，前端无法知道去连接哪个 Pod 的哪个容器。

一个 Tekton TaskRun 会被编译为一个 Pod，Pod 内包含多个容器（每个 Tekton Step 对应一个容器）。用户进入 Pod 时需要先选择要进入哪个容器。

#### 实体改动

| 文件 | 改动 |
|------|------|
| `PipelineRunJobEntity.java` | 新增字段: `podName`, `namespace` (String, nullable), `containers` (String, nullable — JSON 数组文本) |
| `pipeline_run_job` 表 | 新增对应数据库列 |

`containers` 字段存储格式：`["clone-step","build-step","test-step"]` — 即该 Job 对应 TaskRun 下所有 Step 的容器名称 JSON 数组。

#### VO 与服务改动

| 文件 | 改动 |
|------|------|
| `PipelineRunJobVO.java` | 新增字段: `podName`, `namespace`, `containers` (String[]) |
| `PipelineRunService.java` (toJobVo) | 映射新字段；`containers` 字段做 JSON 反序列化 `List<String> → String[]` |

#### 同步时写入

**文件**: `TektonPipelineExecutor.java`

在 `updateJob()` 中，当拿到 `status.getPodName()` 时，收集所有容器的名称：

```java
job.setPodName(pod);
job.setNamespace(this.namespace);
// 收集当前 Job 关联的所有 step 容器名
List<String> containerNames = steps.stream()
        .map(step -> step.getContainer())
        .filter(c -> c != null && !c.isBlank())
        .toList();
job.setContainers(new ObjectMapper().writeValueAsString(containerNames));
runJobMapper.updateById(job);
```

`syncPipelineRun()` 中对每个 TaskRun 也对应更新其 Pod 名和容器列表。

> 注意：容器列表在每次同步时都会刷新覆盖（因为 Tekton Pod 生命中期容器不会增减，覆盖是安全的）。

### 3.3 新增 REST 端点：获取 Job 容器列表（含状态）

为了前端在选容器阶段展示选项+容器状态，新增 REST 端点：

**文件**: `PodExecController.java`

```java
@RestController
@RequestMapping("/pipeline/pipelines/{pipelineId}/runs/{runId}/jobs/{jobId}")
public class PodExecController {

    @GetMapping("/containers")
    public BaseResult<PodContainersVO> getContainers(
            @PathVariable Long pipelineId,
            @PathVariable Long runId,
            @PathVariable Long jobId
    ) {
        // 1. 查询 PipelineRunJobEntity → podName / namespace / containers
        // 2. 验证 tenant 权限
        // 3. 实时查询 K8s Pod 各容器的实际状态
        // 4. 返回 Pod 基础信息 + 容器列表 + 各容器状态 + 推荐的连接模式
    }
}
```

```java
@Data
public class PodContainersVO {
    private String namespace;
    private String podName;
    private List<ContainerInfo> containers;   // 容器列表（含状态）
    private String defaultContainer;           // 建议默认选中的容器
    private List<String> connectModes;         // 可用连接模式 ["exec"] / ["debug_copy"]
    private String recommendedMode;            // 推荐模式 "exec" / "debug_copy"
}

@Data
public class ContainerInfo {
    private String name;                       // 容器名
    private String state;                      // "running" / "terminated" / "waiting"
    private Integer exitCode;                  // 退出码（terminated 时）
    private boolean hasShell;                  // 是否可能有 shell（由镜像特征推测）
}
```

`connectModes` 字段:
- `["exec"]` — 容器在运行中，可以直接 exec
- `["debug_copy"]` — 容器已退出，只能通过 debug copy 进入
- `["exec", "debug_copy"]` — 两种模式均可由用户选择

**containers 端点的 Live 状态查询**:

从 K8s API 实时查询 Pod 的 `ContainerStatus` 列表，而不是只依赖数据库中存储的容器名：

```java
Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
if (pod != null && pod.getStatus() != null) {
    for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
        ContainerInfo info = new ContainerInfo();
        info.setName(cs.getName());
        if (cs.getState() != null) {
            if (cs.getState().getRunning() != null) {
                info.setState("running");
            } else if (cs.getState().getTerminated() != null) {
                info.setState("terminated");
                info.setExitCode(cs.getState().getTerminated().getExitCode());
            } else if (cs.getState().getWaiting() != null) {
                info.setState("waiting");
            }
        }
        // ...
    }
}
```

如果 K8s API 查询失败（Pod 已被删除等），回退使用数据库中存储的容器名列表，所有容器标记为 `state: "unknown"`。

### 3.4 WebSocket 端点：支持指定容器

WebSocket 端点从 `/ws/pipeline/exec/{pipelineId}/{runId}/{jobId}` 改为增加容器选择参数：

```
/ws/pipeline/exec/{pipelineId}/{runId}/{jobId}/{containerName}?token=xxx
```

`PodExecWebSocketHandler` 在 `afterConnectionEstablished` 中从 URI 解析 `containerName`，用它建立 exec 连接。

> 注意：WebSocket URI 中的 `containerName` 需 URL 编码（Tekton 容器名可能含特殊字符如 `-`）。

### 3.3 添加 WebSocket 依赖

**文件**: `server/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 3.5 新增 WebSocket 配置

**新建文件**: `server/src/main/java/.../config/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(podExecHandler(), "/ws/pipeline/exec/{pipelineId}/{runId}/{jobId}/{containerName}")
                .addInterceptors(podExecAuthInterceptor())
                .setAllowedOrigins("*");
    }
}
```

WebSocket URI 包含 `containerName` 路径段，由 Handler 从 `UriTemplate` 中提取。前端在连接时使用用户选中的容器名称。

### 3.6 新增 JWT 握手指令器

**新建文件**: `server/src/main/java/.../ws/PodExecAuthHandshakeInterceptor.java`

- 从 URL query param `token=...` 提取 JWT
- 使用 Spring Security 的 `JwtDecoder` 验证有效性
- 从 JWT 中提取用户 ID 和租户信息存入 session attributes
- token 无效、过期、无权限 → 返回 401 拒绝握手

### 3.7 新增 Pod Exec WebSocket Handler（核心逻辑，含 Debug Copy 降级）

**新建文件**: `server/src/main/java/.../ws/PodExecWebSocketHandler.java`

```
// ====== 全局常量 ======
DEBUG_IMAGE = "busybox:latest"  // 调试镜像，可通过配置覆盖
DEBUG_POD_TTL = 30 * 60 * 1000  // 30分钟 idle 自动清理

// ====== afterConnectionEstablished(session) ======
afterConnectionEstablished(session):
  1. 解析 URI 中的 {pipelineId}/{runId}/{jobId}/{containerName}
  2. 从 session 中获取认证用户信息（JWT 拦截器已验证）
  3. 查询 PipelineRunJobEntity → namespace / podName
  4. 若 podName == null → 发送 { type: "error", message: "Pod 尚未分配" } 并关闭

  5. 实时查询 K8s 获取目标容器的状态:
     Pod pod = client.pods().inNamespace(ns).withName(podName).get()
     ContainerStatus cs = pod.getStatus().getContainerStatuses()
         .stream().filter(s -> s.getName().equals(containerName)).findFirst()

  6. 根据容器状态选择连接模式:
     if cs.getState().getRunning() != null:
         ── MODE EXEC ── 容器正在运行，直接 exec
         ExecWatch watch = client.pods().inNamespace(ns).withName(podName)
             .inContainer(containerName)
             .redirectingInput().redirectingOutput().redirectingError()
             .redirectingErrorChannel().withTTY()
             .exec("sh", "-c", "TERM=xterm-256color bash || TERM=xterm sh")

     elif cs.getState().getTerminated() != null:
         ── MODE DEBUG_COPY ── 容器已退出，创建调试 Pod
         debugPodName = podName + "-debug-" + randomSuffix(6)

         1) 从原 Pod 提取 workspace PVC 挂载信息：
            pod.getSpec().getVolumes().stream()
                .filter(v -> v.getPersistentVolumeClaim() != null)
                .findFirst() → 获取 PVC 名称
           
         2) 创建 Debug Pod:
            Pod debugPod = new PodBuilder()
                .withNewMetadata()
                    .withName(debugPodName)
                    .withNamespace(ns)
                    .withLabels(Map.of("app", "pipeline-debug",
                                       "debug-for", podName,
                                       "debug-container", containerName))
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    .withContainers(new ContainerBuilder()
                        .withName("debug")
                        .withImage(DEBUG_IMAGE)
                        .withCommand("sh", "-c", "sleep infinity")
                        .withNewSecurityContext()
                            .withPrivileged(false)
                        .endSecurityContext()
                        .withStdin(true).withTty(true)
                        // 挂载原 Pod 的 workspace PVC
                        .withVolumeMounts(原Pod的 VolumeMounts)
                        .build())
                    .withVolumes(原Pod的 Volumes)
                    .withServiceAccountName(原Pod的 ServiceAccount)  // 可能需要
                .endSpec()
                .build()
           
          3) client.pods().inNamespace(ns).resource(debugPod).create()
          
          4) 等待 Pod Ready（最多 30s 轮询）:
             client.pods().inNamespace(ns).withName(debugPodName)
                 .waitUntilCondition(p -> p.getStatus() != null
                     && p.getStatus().getContainerStatuses() != null
                     && p.getStatus().getContainerStatuses().stream()
                         .allMatch(s -> s.getState().getRunning() != null),
                     30, TimeUnit.SECONDS)
          
          5) 发送通知给前端:
             session.sendMessage({ type: "mode", mode: "debug_copy",
                 message: "容器已退出，已创建临时调试 Pod: " + debugPodName,
                 debugPodName: debugPodName })
          
          6) 建立 exec 连接到 debug 容器:
             ExecWatch watch = client.pods().inNamespace(ns).withName(debugPodName)
                 .inContainer("debug") ...exec("sh", "-c", ...)

     else (waiting 或未知状态):
         发送 { type: "error", message: "容器状态异常" } 并关闭

  7. 启动两个后台线程桥接 I/O（同 exec 模式）
  8. 保存 watch + session + debugPodName → ConcurrentHashMap

handleMessage(session, message):
  解析 JSON 消息:
  - { "type": "input", "data": "..." } → watch.getInput().write(bytes)
  - { "type": "resize", "cols": 80, "rows": 24 } → watch.resize(cols, rows)

afterConnectionClosed(session, closeStatus):
  1. 关闭对应的 ExecWatch
  2. 中断读取线程
  3. 若存在 debugPodName → 清理:
     client.pods().inNamespace(ns).withName(debugPodName)
         .withGracePeriod(0).delete()
  4. 从 ConcurrentHashMap 移除
```

### 3.7 更新 SecurityConfig

**文件**: `SecurityConfig.java`

WebSocket 端点已在 `anyRequest().authenticated()` 覆盖范围内，但为确保 WebSocket 路径明确可过：

```java
// 允许 WS 端点走我们自己的 JWT 握手拦截器
// 无需额外规则，拦截器会在握手层做验证
```

---

## 4. 前端改动

### 4.1 安装依赖

```bash
cd frontend
npm install @xterm/xterm @xterm/addon-fit
```

### 4.2 新增 PodTerminalDrawer 组件

**新建文件**: `frontend/src/modules/pipeline/components/PodTerminalDrawer.vue`

#### Props

| Prop | 类型 | 说明 |
|------|------|------|
| `show` | boolean | 控制抽屉是否打开 |
| `pipelineId` | string | 流水线 ID |
| `runId` | string | 运行记录 ID |
| `jobId` | string | 任务运行记录 ID |
| `jobName` | string | 任务名称（抽屉标题） |

#### 状态机

```
closed (未打开)
  ↓ show=true
selecting (选择容器) ← 多容器选择界面
  ↓ 用户选择后点击连接
connecting (连接中)
  ↓ 连接成功         ↓ 连接失败
connected          error
  ↓ 断开/关闭          ↓
disconnected       disconnected
  ↓ 重连
selecting (重新选容器)
```

新增 `selecting` 状态：当 Pod 有多个容器时，用户需要选择进入哪一个，然后才发起连接。

#### UI 设计

- **容器**: Naive UI `NDrawer`（右滑出，宽度 700px）
- **头部**: 任务名称 + 状态 + 连接状态标签 + **当前容器名**（连接后）
- **主体区域 — selecting 状态**:
  - 显示 Pod 基础信息摘要（命名空间 / Pod 名称 / Pod 状态）
  - 容器列表以 radio group + 状态徽标展示：
    ```
    ┌────────────────────────────────────────────┐
    │  Pod 信息                                 │
    │  命名空间: hfwas-pipeline                  │
    │  Pod 名称: task-run-xxxx                   │
    │  Pod 状态: Failed (container exited)       │
    │                                            │
    │  选择容器：                                 │
    │  ○ step-clone     🟢 Running   (推荐)      │
    │  ● step-build     🔴 Exited(1)             │
    │  ○ step-test      🔴 Exited(0)             │
    │  ○ step-scan      🔴 Exited(0)             │
    │                                            │
    │  ⓘ 容器已退出，将通过临时 Debug Pod 进入    │
    │                                            │
    │  [  连接终端  ]                             │
    └────────────────────────────────────────────┘
    ```
  - 状态图标: 🟢 Running / 🔴 Exited(exitCode) / ⏳ Waiting / ❓ Unknown
  - 默认选中 running 的容器（若无运行中容器，选中第一个）
  - 如果只有 1 个容器 → 跳过 selecting 直接进入 connecting
  - 选择 terminated 容器时，底部出现提示条：「ⓘ 容器已退出，将通过临时 Debug Pod 进入，结束后自动清理」
  - 连接按钮文案根据模式变化：「连接终端」(exec) / 「创建调试 Pod 并连接」(debug_copy)
- **主体区域 — connecting**: 加载旋转图标 + "正在连接 Pod..."
- **主体区域 — connected**: xterm.js 终端（全高，深色背景 `#1d2129`）
- **主体区域 — error**: 错误消息 + 重试按钮（返回 selecting）
- **主体区域 — disconnected**: 显示断开提示 + 重连按钮（返回 selecting）
- **底部工具栏**: 断开/重连按钮（connected/disconnected 时显示）；切换容器按钮（connected 时：断开后重新 selecting）

#### 核心逻辑

```typescript
// 获取容器列表
async function fetchContainers() {
  const token = await getToken()
  const resp = await fetch(
    `/api/pipeline/pipelines/${pipelineId}/runs/${runId}/jobs/${jobId}/containers`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  const data = await resp.json()
  containers.value = data.data.containers
  defaultContainer.value = data.data.defaultContainer
  selectedContainer.value = defaultContainer.value
  // 仅一个容器 → 直接连接
  if (containers.value.length === 1) connect()
}

// 安装 xterm 终端
const term = new Terminal({
  cursorBlink: true,
  cursorStyle: 'block',
  fontSize: 13,
  fontFamily: 'Menlo, Monaco, "Courier New", monospace',
  theme: { background: '#1d2129', foreground: '#e5e7eb' },
})
const fitAddon = new FitAddon()
term.loadAddon(fitAddon)

// 连接 WebSocket（传入选中的容器名）
function connect() {
  status = 'connecting'
  const token = await getToken()
  ws = new WebSocket(
    `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/ws/pipeline/exec/${pipelineId}/${runId}/${jobId}/${encodeURIComponent(selectedContainer.value)}?token=${token}`
  )
  ws.onopen = () => { status = 'connected'; term.focus(); fitAddon.fit() }
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data)
    if (msg.type === 'output') term.write(msg.data)
    if (msg.type === 'error') { status = 'error'; errorMsg = msg.message }
    if (msg.type === 'container_info') currentContainer = msg.containerName
  }
  ws.onclose = () => { status = 'disconnected' }
  term.onData((data) => ws.send(JSON.stringify({ type: 'input', data })))
  term.onResize(({ cols, rows }) => ws.send(JSON.stringify({ type: 'resize', cols, rows })))
}
```

### 4.3 在任务卡片添加「终端」按钮

**文件**: `YunxiaoFlowCanvas.vue`

在任务操作按钮区新增「终端」按钮。与「查看日志」不同，终端按钮在 **RUNNING 和 FAILED** 状态均可见（失败后进入 Debug Pod 排查是核心场景）：

```html
<button
  v-if="!editable && (job.status === 'RUNNING' || job.status === 'FAILED')"
  type="button"
  class="yx-job-action-btn"
  @click.stop="emit('open-terminal', job.clientKey)"
>
  终端
</button>
```

同时更新 emits 声明：

```typescript
const emit = defineEmits<{
  'insert-stage': [afterIndex: number]
  'add-parallel': [stageKey: string]
  'select-job': [jobKey: string]
  'select-start': []
  'view-log': [jobKey: string]
  'open-terminal': [jobKey: string]  // ← 新增
  remove: [jobKey: string]
}>()
```

### 4.4 集成到 PipelineRunView

**文件**: `PipelineRunView.vue`

1. 新增状态：

```typescript
const terminalJobKey = ref<string | null>(null)

const terminalRunJob = computed(() => {
  if (!terminalJobKey.value) return null
  const job = findEditorJob(stages.value, terminalJobKey.value)
  if (job?.runJobId == null) return null
  return run.value?.jobs.find((item) => String(item.id) === String(job.runJobId)) ?? null
})
```

2. 处理事件：

```typescript
function openTerminal(jobKey: string) {
  terminalJobKey.value = jobKey
}
```

3. 模板嵌入抽屉组件：

```html
<PodTerminalDrawer
  :show="terminalJobKey != null"
  :pipeline-id="pipelineId"
  :run-id="runId"
  :job-id="terminalRunJob?.id ?? ''"
  :job-name="terminalRunJob?.jobName ?? ''"
  @close="terminalJobKey = null"
/>
```

### 4.5 Vite 代理配置

**文件**: `vite.config.ts`

确保开发环境的 Vite proxy 转发 WebSocket 连接：

```typescript
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/ws': { target: 'http://localhost:8080', ws: true },  // ← 新增
}
```

---

## 5. 文件清单

### 后端（新增 4 个文件，修改 8 个文件）

| 操作 | 文件路径 |
|------|----------|
| 改 | `pipeline-core/.../entity/PipelineRunJobEntity.java` — 加 `podName`, `namespace`, `containers` (JSON) 字段 |
| 改 | `pipeline-core/.../dto/PipelineRunJobVO.java` — 加 `podName`, `namespace`, `containers` (String[]) |
| 改 | `pipeline-core/.../config/PipelineExecutorConfiguration.java` — 抽出 K8sClient bean |
| 改 | `pipeline-core/.../executor/TektonPipelineExecutor.java` — 注入 K8sClient bean, populate pod/containers info |
| 改 | `pipeline-core/.../service/PipelineRunService.java` — toJobVo 映射新字段，含 JSON 反序列化 |
| 改 | `server/pom.xml` — 加 `spring-boot-starter-websocket`，`jackson-databind` |
| 改 | `server/.../config/SecurityConfig.java` — WebSocket 路径认证确认 |
| **新** | `server/.../config/WebSocketConfig.java` — 注册 handler |
| **新** | `server/.../ws/PodExecAuthHandshakeInterceptor.java` — JWT 握手拦截 |
| **新** | `server/.../ws/PodExecWebSocketHandler.java` — 核心 exec 桥接，按 `containerName` 参数选择容器 |
| **新** | `pipeline-core/.../controller/PodExecController.java` — `GET .../containers` 返回容器列表 |
| **新** | `pipeline-core/.../dto/PodContainersVO.java` — 容器列表 VO |

### 前端（新增 1 个文件，修改 4 个文件）

| 操作 | 文件路径 |
|------|----------|
| **新** | `frontend/src/modules/pipeline/components/PodTerminalDrawer.vue` — 终端抽屉组件 |
| 改 | `frontend/package.json` — 依赖 + lock |
| 改 | `frontend/src/modules/pipeline/components/YunxiaoFlowCanvas.vue` — 加终端按钮 |
| 改 | `frontend/src/modules/pipeline/views/PipelineRunView.vue` — 集成抽屉 |
| 改 | `frontend/vite.config.ts` — WebSocket proxy |

---

## 6. 验证方案

| 场景 | 步骤 | 预期 |
|------|------|------|
| 基础功能 | 运行流水线 → 点击运行中任务(card)的「终端」按钮 | 抽屉打开，显示容器选择界面或直连（单容器时） |
| 多容器选择 | 点击含多 Step 的任务终端按钮 | 显示容器列表 radio group，各容器有状态徽标 |
| 容器状态展示 | 选择界面中，运行中的容器显示 🟢 Running，已退出的显示 🔴 Exited(1) | 状态徽标正确 |
| 运行中进入 | 选 Running 容器 → 连接终端 | 直接 exec 进入该容器 Shell |
| 命令执行 | 终端输入 `ls`, `env`, `echo hello` | 命令正常执行并回显 |
| **失败 Pod 进入** | Step 失败 → 点击 FAILED 任务的终端按钮 → 选已退出的容器 → 连接 | 后端创建 Debug Pod，前端提示「调试 Pod 已创建」，进入 busybox Shell |
| 调试 Pod 文件查看 | 在 Debug Pod 终端中检查 `/workspace` 目录 | 能看到 Tekton 编译产物、源代码等（通过 PVC 挂载） |
| **调试 Pod 自动清理** | 关闭终端抽屉（WebSocket 断开） | 后端自动删除对应的 Debug Pod |
| Pod 未就绪 | 点击 QUEUED 状态任务的终端按钮 | 按钮不可见 |
| Pod 已成功退出 | 点击 SUCCEEDED 状态任务的终端按钮（加入失败即终止时仍保留按钮） | 按钮可见，进入 Debug Pod |
| 连接断开 | connected → 手动断开 / Pod 退出 | 显示 disconnected，重连回到 selecting |
| 切换容器 | connected → 断开 → 选另一个容器 → 连接 | 正确进入新容器 |
| 窗口 resize | 终端连接时缩放浏览器窗口 | xterm 自适应大小 (fit addon) |
| 单容器自动跳 | 点击仅 1 个容器的任务终端按钮 | 跳过 selecting 直连 |
| Auth 验证 | 使用无效 token 连接 WebSocket | 握手被拒绝，显示认证错误 |
| Containers API | GET .../containers | 返回 namespace/podName/containers[]（每个含 state/exitCode） |
| Debug Pod 标签 | 调试 Pod 被创建后在 K8s 中检查 | 有 `app=pipeline-debug`、`debug-for=原Pod名`、`debug-container=容器名` 标签 |
| 多重调试 | 同时打开两个 FAILED 任务的终端 | 各自独立 Debug Pod，互不干扰 |
| 安全性 | 非该租户的 run 获取容器列表/连 WebSocket | 返回 403 |

---

## 7. 注意事项

1. **日志不打码**: 终端场景不是日志场景，`LogMasker` 不应作用于终端输出。用户在 Pod 内看到的是未经打码的原始输出（与真实 `kubectl exec` 行为一致）。

2. **多容器语义**: Tekton Task 的每个 Step 映射为一个 Pod 内的独立容器。`step-clone`、`step-build` 等容器可能不包含 Shell（例如 `gcr.io/kaniko-project/executor`）。WebSocket handler 使用 `sh -c "TERM=xterm-256color bash || TERM=xterm sh"` 自动降级。若目标容器无任何 Shell，exec 会失败并返回明确错误。

3. **失败 Pod 调试（Debug Copy 核心场景）**: Step 失败后容器退出，`kubectl exec` 无法进入。后端通过以下方式补救：
   - 创建新 Pod（使用 busybox 镜像），挂载原 Pod 的 workspace PVC
   - 原 Pod 的 `/workspace` 下保留编译产物和源代码，用户进入 Debug Pod 后可以 `ls /workspace`、`cat` 日志文件等
   - Debug Pod 命名: `{tektonPod}-debug-{random6}`，带标签 `app=pipeline-debug`, `debug-for=原Pod名`
   - 生命周期与 WebSocket Session 绑定 — 断开连接即自动 `delete()`（`gracePeriod=0`）
   - 若因异常未能清理，Debug Pod 可通过 K8s 标签 `app=pipeline-debug` 统一清理

3. **容器列表刷新时机**: 容器列表在 Pod 分配时一次性确定（Tekton Pod 生命期内容器不会增减）。同步写入只需在首次拿到 podName 时完成，后续同步可跳过容器列表更新（或覆盖更新，成本很低）。

4. **containers 字段存储**: 使用 Jackson `ObjectMapper` 在实体层面做 `List<String> ↔ JSON String` 的转换。推荐在 Entity 的 getter/setter 中处理，或使用 MyBatis-Plus 的 TypeHandler。

5. **WebSocket 容器参数编码**: 容器名在 WebSocket URI 中可能含特殊字符（如 `step-clone`），需前端用 `encodeURIComponent()` 编码，后端用 Spring 的 `@PathVariable` 自动解码。

6. **单容器优化**: 当 `containers.length === 1` 时，前端自动跳过 selecting 状态，直接进入 connecting 状态，减少用户点击次数。

7. **超时保护**: K8s exec 空闲超过 30 分钟应自动断开（可通过 `ExecWatch.close()` 和 session close 实现）。

8. **TTY 宽度/高度**: resize 消息需根据 xterm 实际 `cols/rows` 传递，确保 shell 编辑器（vim、nano 等）显示正常。

9. **K8sClient Bean 为 null 时的处理**: 若 `kubernetesClient()` bean 因无 kubeconfig 为 null，PodExecController 的容器列表 API 和 WebSocket handler 均在握手阶段返回错误信息「未配置执行集群」。