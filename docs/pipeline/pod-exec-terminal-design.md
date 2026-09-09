# 流水线 Pod 终端调试功能 — 设计与实现方案

> 日期：2026-09-09  
> 状态：待实施  
> 版本：v0.2  
> 关联： [2026-09-07-pipeline-design.md](../superpowers/specs/2026-09-07-pipeline-design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-09 | 初版：多容器选择 + 失败 Pod Debug Copy 降级 + WebSocket 终端 |
| v0.2 | 2026-09-09 | 审查修订：连接模式改为 exec / ephemeral / debug_pod / unavailable；WS 独立鉴权；异步就绪；资源上限与 GC |

---

## 1. 背景与目标

### 1.1 需求

用户在流水线执行过程中，需要直接点击任务卡片上的「终端」按钮，在浏览器中打开一个交互式 Shell，**进入该任务对应的 K8s 现场执行命令** — 即 `kubectl exec` / `kubectl debug` 的 Web 界面等价。

失败后排查是核心场景。实现必须尊重本仓库的真实 workspace 形态，不能假设「新建一颗 Pod 挂上原卷」一定可行。

### 1.2 现状

- **TektonPipelineExecutor** 已通过 fabric8 Kubernetes Client 跟踪 Pod（从 `TaskRun.getStatus().getPodName()` 获取 pod 名），但 Pod 信息仅存留在 watch 线程内存中，未持久化、未通过 API 暴露
- **串行流水线**编译为 `TektonMode.TASK`，workspace 绑定 **emptyDir**（见 `TektonManifests.taskRun`）
- **并行 / 多 Job 流水线**编译为 PipelineRun，workspace 绑定 **ReadWriteOnce PVC**（见 `TektonManifests.workspaceClaim`）
- Tekton workspace 名为 `source`，源码目录为 `/workspace/source/src`（不是 `/workspace`）
- **前端 PipelineRunView** 展示任务卡片（YunxiaoFlowCanvas），已有「查看日志」按钮和右侧日志抽屉，但没有交互式终端入口
- 前后端均无 WebSocket 基础设施；前端无 xterm.js
- `SecurityConfig` 为 `anyRequest().authenticated()` + oauth2 JWT（只认 `Authorization` 头）；浏览器原生 WebSocket 不能自定义该头

---

## 2. 整体架构

```
浏览器 xterm.js
    │ WebSocket（Sec-WebSocket-Protocol 带 JWT，禁止 query token）
    ▼
握手拦截器（Origin + JWT + 租户 + pipeline/run/job 归属）
    ▼
Spring WebSocket Handler  （先回 mode，K8s 就绪后再回 ready）
    ├─ exec        原 Pod 在、容器 Running 且有 shell → fabric8 ExecWatch
    ├─ ephemeral   原 Pod 在、无 shell / 已退出       → 向原 Pod 注入 ephemeral 容器后 exec
    ├─ debug_pod   原 Pod 已删、workspace PVC 仍在     → 创建临时 Debug Pod 后 exec
    └─ unavailable 原 Pod 已删、workspace 为 emptyDir  → 明确失败，只能看日志
```

### 2.1 连接模式（后端根据 Live 状态决定，前端不猜）

| 原 Pod | 目标容器 | Workspace | 模式 | 说明 |
|--------|----------|-----------|------|------|
| 还在 | Running 且有 shell | 任意 | `exec` | `inContainer.exec`，等价 `kubectl exec -it` |
| 还在 | Running 但无 shell，或 Terminated / 可观察现场 | 任意 | `ephemeral` | 等价 `kubectl debug -it --target=`：共享原 Pod 的 emptyDir / PVC / 网络，**无 RWO 冲突** |
| 已删除 | — | PVC 仍在（Pipeline 模式） | `debug_pod` | 这时才创建临时 Pod，只挂 workspace PVC |
| 已删除 | — | emptyDir（TASK 模式） | `unavailable` | 现场已丢，UI 说明只能看日志，禁止假装能进终端 |

**禁止**在原 Pod 仍存在时创建第二颗 Pod 去挂 RWO PVC：本仓库 PVC 是 `ReadWriteOnce`，原 TaskRun Pod 未删时 Debug Pod 会 `FailedAttachVolume`。

**禁止**把原 Pod 的全部 Volume / Secret / ServiceAccount 复制进 Debug 容器。debug_pod 只挂目标 step 的 workspace PVC，使用独立低权限 SA。

### 2.2 核心流程

1. 用户在 PipelineRunView 点击任务卡片的「终端」按钮（**RUNNING / FAILED / SUCCEEDED / CANCELLED** 且已分配 `podName` 时可见）
2. 前端打开 PodTerminalDrawer，调用 `GET .../containers` 获取容器列表、Live 状态、**推荐模式**
3. 用户选择容器（单容器可自动进入 connecting，但仍要展示模式提示）
4. 前端通过 WebSocket 连接后端；JWT 放在 `Sec-WebSocket-Protocol: bearer.<jwt>`，**禁止** `?token=`
5. 握手拦截器校验 Origin、JWT、租户、资源归属；失败则拒绝升级
6. Handler 查 Live 状态，选择上表中的一种模式：
   - 立即发送 `{ type: "mode", mode, message, workspacePath }`
   - **不得**在握手线程里同步阻塞 30s；ephemeral / debug_pod 的创建在后台完成
   - 目标 shell 真正 attach 后再发送 `{ type: "ready" }`
7. 双向桥接：键盘 JSON 控制帧 → K8s；K8s 输出以 **二进制帧** 回给 xterm
8. 关闭抽屉或空闲超时 → 关闭 ExecWatch；删除本次创建的 ephemeral 容器或 Debug Pod；写审计日志

---

## 3. 后端改动

### 3.1 暴露 KubernetesClient 为 Spring Bean

**文件**: `pipeline-core/.../config/PipelineExecutorConfiguration.java`

将 `KubernetesClient` 抽成独立 Bean，供 Executor 与 Pod Exec 共用。**禁止** `@Bean` 方法 `return null`（注入会 NPE）。无 kubeconfig 时不注册该 Bean，用 `ObjectProvider` 表达缺省。关机必须 `close()`。

无 kubeconfig 时**不要注册该 Bean**（自定义 `Condition` 判断文件存在），也**不要** `return null` 或抛错导致启动失败：

```java
@Bean(destroyMethod = "close")
@ConditionalOnMissingBean
@Conditional(PipelineKubeconfigPresent.class) // 文件存在才注册
public KubernetesClient kubernetesClient(
        @Value("${pipeline.kubeconfig:}") String kubeconfig
) throws IOException {
    Config config = Config.fromKubeconfig(Files.readString(Path.of(kubeconfig)));
    return new KubernetesClientBuilder().withConfig(config).build();
}
```

`pipelineExecutor()` 改为：

```java
KubernetesClient client = kubernetesClients.getIfAvailable();
if (client == null) {
    return new UnavailablePipelineExecutor();
}
return new TektonPipelineExecutor(client, ...);
```

PodExecController / WebSocket Handler 同样通过 `ObjectProvider<KubernetesClient>` 注入；`getIfAvailable() == null` 时返回「未配置执行集群」，不要 NPE。全进程只允许一份 client。

### 3.2 持久化 Pod / 容器信息（支持多容器）

**背景**: 目前 `PipelineRunJobVO` / `PipelineRunJobEntity` 不包含 `podName` / `namespace` / 容器列表。

一个 Tekton TaskRun 对应一个 Pod；Pod 内除 Step 容器外还有 Tekton 内部容器（`place-scripts` 等）。**数据库只存该 Job 的 step 容器名**，Live 查询再补状态，并过滤内部容器。

#### 实体与表

| 文件 | 改动 |
|------|------|
| `PipelineRunJobEntity.java` | 新增 `podName`, `namespace`（String, nullable）, `containers`（String, nullable — JSON 数组文本） |
| `backend/server/src/main/resources/db/pipeline-schema.sql` | `pipeline_run_job` 的 `CREATE TABLE` **直接加列**（绿野项目，不做旧行兼容） |

列名：`pod_name`、`namespace`、`containers`。`containers` 存储格式：`["clone","build"]` — TaskRun 下该 Job 对应的 Step 容器名。

#### VO 与服务

| 文件 | 改动 |
|------|------|
| `PipelineRunJobVO.java` | `podName`, `namespace`, `containers` (`String[]`) |
| `PipelineRunService.java` (`toJobVo`) | 映射；`containers` JSON 反序列化。推荐 MyBatis-Plus TypeHandler 或 Entity getter/setter，**不要**在 `updateJob` 里每次 `new ObjectMapper()` |

#### 同步时写入

**文件**: `TektonPipelineExecutor.java`

`podName` **首次非空写入后**，`containers` 不再随 2s watch 刷新（Tekton Pod 生命期内容器不会增减）。仅当 `job.getPodName()` 为空且本次 `pod` 非空时：

```java
job.setPodName(pod);
job.setNamespace(this.namespace);
List<String> containerNames = steps.stream()
        .map(StepState::getContainer)
        .filter(c -> c != null && !c.isBlank())
        .toList();
job.setContainers(toJson(containerNames)); // 注入的 ObjectMapper / TypeHandler
```

`syncPipelineRun()` 对每个 child TaskRun 同样处理。

### 3.3 REST：获取 Job 容器列表（含状态与推荐模式）

**文件**: `pipeline-core/.../controller/PodExecController.java`

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
        // 1. definitionService.requireOwned(pipelineId)
        // 2. 校验 run 属于 pipeline、job 属于 run（否则 404，防 IDOR）
        // 3. 读 PipelineRunJobEntity → podName / namespace / containers
        // 4. Live 查 K8s Pod；过滤非 step 容器
        // 5. 按 §2.1 计算每条容器的 recommendedMode / connectModes
    }
}
```

```java
@Data
public class PodContainersVO {
    private String namespace;
    private String podName;
    private String podExists;                  // "true" / "false" / "unknown"
    private String workspaceKind;              // "pvc" / "emptydir" / "unknown"
    private String workspacePath;              // 固定提示："/workspace/source/src"
    private List<ContainerInfo> containers;
    private String defaultContainer;
}

@Data
public class ContainerInfo {
    private String name;
    private String state;                      // "running" / "terminated" / "waiting" / "unknown"
    private Integer exitCode;
    private boolean hasShell;                  // 见 §8 hasShell
    private String recommendedMode;            // exec / ephemeral / debug_pod / unavailable
    private String unavailableReason;          // mode=unavailable 时的中文说明
}
```

不再返回「用户可选 exec 或 debug_copy」。模式由后端按 Live 状态决定；前端只展示说明。

**Live 查询规则**:

1. `client.pods().inNamespace(ns).withName(podName).get()`
2. 若 Pod 存在：用 `containerStatuses` 填 state/exitCode，但 **只保留数据库 `containers` 列表中的名字**（过滤 `place-scripts`、`sidecar-*` 等）
3. 若 Pod 不存在：`podExists=false`，容器 state 全为 `unknown`，按 workspace 类型给出 `debug_pod` 或 `unavailable`
4. 判断 workspace：看原 Pod spec（若还在）或本次流水线模式（TASK → emptydir，Pipeline → pvc）；PVC 名与 `run.tektonName + "-ws"` 对齐，再 `client.persistentVolumeClaims()...get()` 确认是否仍在

`hasShell`：对 Running 容器，维护一小份无 shell 镜像前缀黑名单（kaniko、`gcr.io/distroless`、`curlimages/curl` 等）。**最终以 exec 尝试为准**：exec 因无 shell 失败则自动降到 `ephemeral`，不要只报「容器状态异常」。

### 3.4 WebSocket 依赖与端点

**文件**: `server/pom.xml` — 只加 `spring-boot-starter-websocket`（`jackson-databind` 已由 Spring Boot 提供，不要重复声明）。

```
/ws/pipeline/exec/{pipelineId}/{runId}/{jobId}/{containerName}
```

`containerName` 用 `encodeURIComponent`；Tekton 名一般是 DNS 标签，真正要防的是 `/` 与空格。

### 3.5 WebSocket 配置

**新建文件**: `server/src/main/java/.../config/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(podExecHandler(), "/ws/pipeline/exec/{pipelineId}/{runId}/{jobId}/{containerName}")
                .addInterceptors(podExecAuthInterceptor())
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
        // 生产再通过配置收紧；禁止 setAllowedOrigins("*")
    }
}
```

### 3.6 JWT 握手拦截器（独立于 oauth2 资源服务器）

**新建文件**: `server/src/main/java/.../ws/PodExecAuthHandshakeInterceptor.java`

浏览器不能给原生 WebSocket 设 `Authorization`。现有 `oauth2ResourceServer` **不会**读 query `token=`。因此：

1. **`SecurityConfig` 必须** `.requestMatchers("/ws/pipeline/exec/**").permitAll()`，注释写明：鉴权在握手拦截器，否则升级请求在过滤器链就会 401。
2. **禁止** JWT 放在 query string（会进 Vite / 反向代理 / access log）。
3. 拦截器从 `Sec-WebSocket-Protocol` 读取 `bearer.<jwt>`（前端 `new WebSocket(url, ['bearer.' + token])`），用 `JwtDecoder` 校验。
4. 回显该 subprotocol，握手才能成功。
5. 将 JWT 转为与 HTTP 相同的 `AuthUserPrincipal`，调用 `TenantContextService.resolveAndValidate`（不要假设 `TenantContextFilter` 的 ThreadLocal 在 WS I/O 线程上仍可用）。
6. 再做资源归属（与 REST 一致）：
   - `definitionService.requireOwned(pipelineId)`
   - run 属于该 pipeline
   - job 属于该 run
7. 用户 ID、租户 ID、pipelineId/runId/jobId 写入 session attributes。
8. Origin 不在允许列表、token 无效/过期、无权限 → 拒绝握手（401/403）。

握手通过后写一条审计日志：谁、何时、哪个 pipeline/run/job/container、client IP。真正选用的 mode 在 Handler 里再补一条。

### 3.7 SecurityConfig

**文件**: `SecurityConfig.java`

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/health/check").permitAll()
        .requestMatchers("/ws/pipeline/exec/**").permitAll()
        // ... 其余规则不变
        .anyRequest().authenticated())
```

`permitAll` 只放开升级入口；没有合法 JWT + 资源归属仍进不了 Handler 的 K8s 调用。

### 3.8 Pod Exec WebSocket Handler

**新建文件**: `server/src/main/java/.../ws/PodExecWebSocketHandler.java`

#### 资源与超时（必须落地，不能只写常量）

| 项 | 值 |
|----|-----|
| 每用户并发 session | 4 |
| 全局并发 session | 16 |
| 空闲超时 | 30 分钟无 input / 无 resize 则关闭 |
| 应用层 ping | 每 30s 发 `{ type: "ping" }`，前端回 `{ type: "pong" }`；连续 2 次无响应则断开 |
| Debug 镜像 | 固定 `busybox:1.37.0`（可配置覆盖），`imagePullPolicy=IfNotPresent`，禁止 `latest` |
| Debug Pod 生存 | `activeDeadlineSeconds=1800` + 标签 GC |
| Debug Pod 名 | `dbg-{jobId}-{rand6}`，保证 ≤ 63 字符（禁止 `{tektonPodName}-debug-...`） |

超限：发送 `{ type: "error", message: "终端连接数已达上限" }` 并关闭。

I/O 使用**有界**线程池（或 NIO），禁止每 session `new Thread` × 2 的无界 cached pool。

#### 协议

| 方向 | 帧 | 内容 |
|------|----|------|
| 服务端 → 浏览器 | 文本 JSON | `mode` / `ready` / `error` / `ping` |
| 服务端 → 浏览器 | **二进制** | PTY 原始输出（含 ANSI），**不要** JSON 包一层 |
| 浏览器 → 服务端 | 文本 JSON | `input` / `resize` / `pong` |

`ws.onopen` 只表示传输层连通。前端必须收到 `ready` 才进入 `connected` 并 `term.focus()`。

#### afterConnectionEstablished（禁止长时间阻塞）

```
afterConnectionEstablished(session):
  1. 解析 URI；读取握手期写入的用户与资源 ID
  2. 再次确认 KubernetesClient 可用
  3. 读 PipelineRunJobEntity；podName == null → error "Pod 尚未分配" 并关闭
  4. Live 判定模式（§2.1）；立刻 session.send { type: "mode", mode, message, workspacePath }
  5. 将 attach 提交到有界执行器（不要占用 Tomcat WS worker 等待 K8s）:
       exec        → 立即 ExecWatch
       ephemeral   → 注入 ephemeral 容器，watch Ready 后再 exec（超时 30s，失败 error）
       debug_pod   → 创建 Debug Pod（见下），Ready 后再 exec
       unavailable → error(unavailableReason) 并关闭
  6. attach 成功 → send { type: "ready" }；开始把 ExecWatch 输出以二进制帧转发
  7. 登记 session 到 ConcurrentHashMap（含 lastActiveAt、mode、debugPodName、ephemeralName）
  8. 审计：userId, tenantId, pipelineId, runId, jobId, container, mode
```

**exec**:

```
ExecWatch watch = client.pods().inNamespace(ns).withName(podName)
    .inContainer(containerName)
    .redirectingInput().redirectingOutput().redirectingError()
    .redirectingErrorChannel().withTTY()
    .exec("sh", "-c", "exec env TERM=xterm-256color bash || exec env TERM=xterm sh")
```

无 shell（进程立即退出 / 创建失败）→ **同一 session 内降级到 ephemeral**，再发一次 `mode`。

**ephemeral**（原 Pod 还在时的主路径）：

向原 Pod 注入一颗 ephemeral 容器（fabric8 ephemeralContainers patch，等价 `kubectl debug --target=<container>`）：

- 镜像：`busybox:1.37.0`
- `target`：用户选中的 step 容器（共享其挂载卷，含 emptyDir 与 RWO PVC）
- 不复制 Secret、不使用流水线 Deploy 的高权限 SA
- 名称：`dbg-{rand6}`（符合 DNS 标签）
- Ready 后对该 ephemeral 容器 exec
- session 关闭时删除该 ephemeral 容器（能删则删；K8s 对 ephemeral 的删除能力有限，故必须设较短的命令 `sleep 1800` 作为兜底）

**debug_pod**（仅原 Pod 已删且 PVC 仍在）：

```
1. 确认 PVC 存在；不存在 → unavailable
2. 创建 Pod:
     metadata.name = dbg-{jobId}-{rand6}
     labels: app=pipeline-debug, debug-for-job={jobId}, tenant={tenantId}
     spec.activeDeadlineSeconds = 1800
     spec.restartPolicy = Never
     容器: busybox:1.37.0, command sleep 1800, privileged=false
     只挂载 workspace PVC → /workspace/source
     ServiceAccount: 独立低权限（默认 namespace SA），禁止抄原 Pod SA
3. 后台 wait Ready（超时 30s）—— 不在 WS worker 上 waitUntilCondition
4. exec 进 debug 容器
```

session 关闭：`withGracePeriod(0).delete()`。另需 **定时 GC**：列出 `app=pipeline-debug` 且创建超过 30 分钟的 Pod 并删除（覆盖进程崩溃 / 握手后浏览器直接杀页）。

**waiting** 且原 Pod 还在：优先 ephemeral（可以看到已完成 step 写在 emptyDir/PVC 里的文件），而不是直接 error。

#### 消息

```
handleMessage:
  ping 超时与 lastActiveAt 更新
  { type: "input", data }  → watch.getInput().write(UTF-8)
  { type: "resize", cols, rows } → watch.resize(cols, rows)（确认所用 fabric8 版本确有 resize）
  { type: "pong" } → 记录心跳
```

#### 关闭

```
afterConnectionClosed:
  关闭 ExecWatch
  取消执行器任务
  删除本次 Debug Pod / 尝试删除 ephemeral 容器
  从 map 移除
  审计 session 结束
```

服务重启后：启动时跑一次 `app=pipeline-debug` 全量 GC。

---

## 4. 前端改动

### 4.1 安装依赖

```bash
cd frontend
npm install @xterm/xterm @xterm/addon-fit
```

### 4.2 PodTerminalDrawer

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
closed
  ↓ show=true
selecting
  ↓ 用户连接（或单容器自动 connecting）
connecting          ← ws.onopen 仍停留在此；展示 mode 文案
  ↓ type=ready           ↓ type=error / 握手失败
connected              error
  ↓ 断开                 ↓ 重试
disconnected / selecting
```

#### UI

- Naive UI `NDrawer`，宽度 700px
- 头部：任务名 + 连接状态 + 当前容器 + 当前 mode
- **selecting**：Pod 摘要（namespace / podName / podExists / workspaceKind）+ 容器 radio + 状态徽标
  - 推荐选中 Running 容器；否则第一个非 unavailable
  - 底部提示随 `recommendedMode` 变化：
    - `exec`：直接进入该容器
    - `ephemeral`：原容器已退出或无 shell，将注入临时调试容器（共享原卷）
    - `debug_pod`：原 Pod 已删除，将挂载 workspace PVC 创建临时 Pod
    - `unavailable`：现场已不可用（TASK emptyDir 且 Pod 已删），禁用连接，引导去看日志
  - 工作区路径提示固定：`文件在 /workspace/source/src`
- 单容器且 mode ≠ unavailable：自动 connecting，**不要跳过 mode 提示**（connecting 区显示同一句话）
- **connecting**：spinner + 后端 `mode.message`（例如「正在注入调试容器…」）
- **connected**：xterm，背景 `#1d2129`
- **error / disconnected**：说明 + 回到 selecting

#### 核心逻辑

```typescript
function connect() {
  status = 'connecting'
  disposeTermIO() // 重连前去掉旧的 onData / onResize，避免一次按键多发
  const token = await getToken()
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${proto}://${location.host}/api/ws/pipeline/exec/${pipelineId}/${runId}/${jobId}/${encodeURIComponent(selectedContainer.value)}`
  ws = new WebSocket(url, [`bearer.${token}`])
  ws.binaryType = 'arraybuffer'
  ws.onopen = () => { /* 仍为 connecting，等待 ready */ }
  ws.onmessage = (ev) => {
    if (typeof ev.data !== 'string') {
      term.write(new Uint8Array(ev.data))
      return
    }
    const msg = JSON.parse(ev.data)
    if (msg.type === 'mode') {
      currentMode = msg.mode
      connectingHint = msg.message
      workspacePath = msg.workspacePath
    }
    if (msg.type === 'ready') {
      status = 'connected'
      term.focus()
      fitAddon.fit()
    }
    if (msg.type === 'error') { status = 'error'; errorMsg = msg.message }
    if (msg.type === 'ping') ws.send(JSON.stringify({ type: 'pong' }))
  }
  ws.onclose = () => { status = 'disconnected' }
  termDataDisp = term.onData((data) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'input', data }))
    }
  })
  termResizeDisp = term.onResize(({ cols, rows }) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'resize', cols, rows }))
    }
  })
}
```

REST `GET .../containers` 继续走现有 `Authorization: Bearer`（axios/fetch），不要用裸 fetch 漏掉公共错误处理；优先复用 `shared/api/request`。

### 4.3 任务卡片「终端」按钮

**文件**: `YunxiaoFlowCanvas.vue`

```html
<button
  v-if="!editable && terminalAllowed(job.status)"
  type="button"
  class="yx-job-action-btn"
  @click.stop="emit('open-terminal', job.clientKey)"
>
  终端
</button>
```

```typescript
function terminalAllowed(status?: string) {
  return status === 'RUNNING' || status === 'FAILED'
      || status === 'SUCCEEDED' || status === 'CANCELLED'
}
```

QUEUED / WAITING_APPROVAL 不可见。点开后若 API 判定 `unavailable`，抽屉内说明原因，而不是隐藏按钮（成功/取消后用户仍可能想试，由后端告诉现场在不在）。

emits 增加 `'open-terminal': [jobKey: string]`。

### 4.4 集成 PipelineRunView

**文件**: `PipelineRunView.vue`

与日志抽屉并列：`terminalJobKey` → 解析 `terminalRunJob` → 渲染 `PodTerminalDrawer`。关闭时 `terminalJobKey = null`。

### 4.5 Vite 代理

**文件**: `frontend/vite.config.ts`

前端 WS URL 是 `/api/ws/...`，现有代理已把 `/api/` rewrite 成后端路径，后端端口是 **8089**。

**只给现有 `/api/` 代理加 `ws: true`**。不要新增 `/ws` → `8080`（匹配不到 URL，端口也不对）。

```typescript
proxy: {
  '/api/': {
    target: 'http://localhost:8089',
    changeOrigin: true,
    ws: true,
    rewrite: (p) => p.replace(/^\/api/, ''),
    configure: (proxy) => { /* 现有 X-Forwarded-For 保持不变 */ },
  },
}
```

---

## 5. 文件清单

### 后端（新增约 6 个文件，修改 9 个文件）

| 操作 | 文件路径 |
|------|----------|
| 改 | `backend/server/src/main/resources/db/pipeline-schema.sql` — `pipeline_run_job` 加 `pod_name` / `namespace` / `containers` |
| 改 | `pipeline-core/.../entity/PipelineRunJobEntity.java` |
| 改 | `pipeline-core/.../dto/PipelineRunJobVO.java` |
| 改 | `pipeline-core/.../config/PipelineExecutorConfiguration.java` — Client Bean + ObjectProvider |
| 改 | `pipeline-core/.../executor/TektonPipelineExecutor.java` — 首次写入 pod/containers |
| 改 | `pipeline-core/.../service/PipelineRunService.java` — toJobVo |
| 改 | `server/pom.xml` — `spring-boot-starter-websocket` |
| 改 | `server/.../config/SecurityConfig.java` — permitAll `/ws/pipeline/exec/**` |
| **新** | `server/.../config/WebSocketConfig.java` |
| **新** | `server/.../ws/PodExecAuthHandshakeInterceptor.java` |
| **新** | `server/.../ws/PodExecWebSocketHandler.java` |
| **新** | `server/.../ws/PipelineDebugPodGc.java` — 启动 + 定时清理 `app=pipeline-debug` |
| **新** | `pipeline-core/.../controller/PodExecController.java` |
| **新** | `pipeline-core/.../dto/PodContainersVO.java` |

### 前端（新增 1 个文件，修改 4 个文件）

| 操作 | 文件路径 |
|------|----------|
| **新** | `frontend/src/modules/pipeline/components/PodTerminalDrawer.vue` |
| 改 | `frontend/package.json` — 依赖 + lock |
| 改 | `frontend/src/modules/pipeline/components/YunxiaoFlowCanvas.vue` |
| 改 | `frontend/src/modules/pipeline/views/PipelineRunView.vue` |
| 改 | `frontend/vite.config.ts` — `/api/` 增加 `ws: true` |

---

## 6. 验证方案

| 场景 | 步骤 | 预期 |
|------|------|------|
| 基础功能 | 运行中任务点「终端」 | 抽屉打开；多容器选列表，单容器进入 connecting 并显示 mode 文案 |
| 运行中有 shell | 选 Running 容器 | `mode=exec`，收到 `ready` 后再出现可输入 shell |
| 运行中无 shell | kaniko / curl 等镜像 | exec 失败后同一 session 降级 `ephemeral`，能进 busybox |
| **失败现场（Pod 还在）** | FAILED 任务选已退出容器 | `ephemeral`，**不是**新建 Debug Pod；能看到 emptyDir 或 PVC 上的文件 |
| TASK 模式且 Pod 已删 | SUCCEEDED 后 Tekton 清掉 Pod | `unavailable`，提示只能看日志，不创建 Pod |
| Pipeline 模式且 Pod 已删、PVC 在 | 点终端 | `debug_pod`，connecting 显示创建中，ready 后 `ls /workspace/source/src` 看得到源码 |
| RWO 不冲突 | 原 Pod 仍在时进失败 step | 不得出现第二颗 Pending（FailedAttachVolume）的 debug Pod |
| Debug Pod 清理 | 关闭抽屉 | Debug Pod 被删；杀浏览器进程后 30min 内 GC 也会删 |
| 按钮可见性 | QUEUED 无按钮；RUNNING/FAILED/SUCCEEDED/CANCELLED 有 | 与 §4.3 一致 |
| 工作区路径 | 终端内 | 文档与 UI 均指向 `/workspace/source/src` |
| 内部容器 | 选择列表 | 无 `place-scripts` / sidecar |
| resize | 缩放窗口 | vim/nano 正常 |
| 重连 | 断开再连 | 一次按键只发一份 input |
| Auth | 无效 / 过期 JWT | 握手拒绝 |
| IDOR | 改 URI 中别人的 pipelineId/runId/jobId | 403 |
| Query token | `?token=` 连接 | **失败**（不支持） |
| 并发上限 | 同一用户开第 5 条 | error「连接数已达上限」 |
| 开发代理 | `npm run dev` 连终端 | `/api/` + `ws: true` + 8089 可升级，不依赖 `/ws` 代理 |
| 审计 | 连接一次 | 日志含 userId、资源 ID、mode |

---

## 7. 注意事项

1. **日志不打码**：终端不是日志管道，`LogMasker` 不作用于 PTY 输出（与 `kubectl exec` 一致）。审计只记元数据，不记终端内容。

2. **exec 仍是高权限操作**：运行中容器环境变量可能含 `GIT_PASSWORD`。产品接受与 `kubectl exec` 同等风险，但必须：租户 + 资源归属、并发上限、审计。debug_pod / ephemeral **不得**复用流水线 Task 的 SA 去操作集群。

3. **ephemeral 是失败排查主路径**；debug_pod 只覆盖「Pod 已删 + PVC 还在」。TASK + emptyDir + Pod 已删 = 不可用。

4. **同一次 PipelineRun 的多个 Job 共享一块 RWO PVC**。两个 FAILED 任务若原 Pod 都还在，各自 ephemeral 挂在**自己的 TaskRun Pod**上，互不抢卷。仅 debug_pod 模式不要对同一 PVC 并行创建两颗 Debug Pod。

5. **容器列表**：首次拿到 `podName` 时写入 DB，之后 watch 不再刷 `containers`。

6. **握手线程**：任何 K8s wait 都放到有界执行器；先 `mode` 后 `ready`。

7. **TTY resize**：按 xterm 的 `cols/rows` 调用 fabric8 `ExecWatch.resize`；实现时核对当前 fabric8 版本 API。

8. **生产 Origin**：`WebSocketConfig` 的 allowedOriginPatterns 与 CORS 同源策略对齐，随部署配置，不要 `*`。
