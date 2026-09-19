# 代码审查报告：CPU 飙升 / 内存告警 / 对外内存暴涨 / OOM 风险

> 日期：2026-09-19
> 版本：v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-19 | 初版：审查 761 个 Java 源文件，深入阅读 30+ 个高风险模块 |

---

## 审查范围

对所有 761 个 Java 源文件进行了系统性审查，重点审查以下高风险模块：

| 模块 | 风险类别 |
|------|---------|
| WebSocket 处理器 | 长连接泄漏、线程膨胀、忙等 |
| 图片处理（ImageMagick / ImageIO） | 堆内/对外内存、子进程 runaway |
| 文件解析（Tika / OCR / RapidOCR） | Native 内存、大对象分配 |
| 流水线执行（Tekton） | 线程泄漏、轮询开销 |
| API 测试引擎 | 大响应体 OOM |
| Prometheus 监控 | 查询风暴、结果解析 |
| 脚本沙箱（GraalVM） | 引擎创建开销 |
| 后台心跳任务 | 调度器阻塞 |

---

## 发现总览

| 严重度 | 数量 | 说明 |
|--------|------|------|
| 🔴 **高危** | 4 | 需立即修复 |
| 🟠 **中危** | 6 | 有计划内修复 |
| 🟢 **低危** | 5 | 最佳实践优化 |

---

## 🔴 高危（需立即修复）

### 1. `LogTailHandler` — 无界线程池 → OOM / 线程飚升

**文件**：`backend/container-core/src/main/java/com/hfwas/devops/container/ws/LogTailHandler.java:33-37`

```java
private final ExecutorService logExecutor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "log-tail-");
    t.setDaemon(true);
    return t;
});
```

**风险**：`newCachedThreadPool()` 每来一个 WebSocket 连接就创建一个新线程，**无上限**。大量用户同时打开 Pod 日志实时 tail 时，会创建数千个线程，每个线程持有 blocking read + K8s `LogWatch` 连接。线程栈默认 ~1MB/线程，堆外内存耗尽导致 OOM。

**场景**：100 个并发日志连接 → 100 线程 → 100MB+ 线程栈虚拟内存 + 100 个 K8s watch 连接。

**修复建议**：
- 改用 `newFixedThreadPool(max, queue)` + 有界工作队列 + 拒绝策略
- 或复用少量线程用 Selector 模式轮询多个 LogWatch
- 设置每个 session 的 `maxLogBytes` 读取上限

---

### 2. `TektonPipelineExecutor` — 无界 watch 线程池 → OOM

**文件**：`backend/pipeline-core/src/main/java/com/hfwas/devops/pipeline/executor/TektonPipelineExecutor.java:83-87`

```java
private final ExecutorService watchPool = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "pipeline-tekton-watch");
    thread.setDaemon(true);
    return thread;
});
```

**风险**：每次 `submit(runId)` 提交一个长期运行的 watch 线程（`watch()` → 每 2s 轮询 Tekton 状态，持续到流水线结束）。大量并行流水线（50+ 并发 run）会创建等量线程，且 watch 线程内部包含 API 调用和 DB 更新，生命周期长。

**修复建议**：
- 有界线程池 + 及时清理已完成任务的线程
- 改为单线程定时器统一轮询所有活跃 run
- 设置最大并发 run 数

---

### 3. `HttpDebugEngine` — `readAllBytes()` 先读后截断 → OOM

**文件**：`backend/api-test-core/src/main/java/com/hfwas/devops/apitest/debugger/engine/HttpDebugEngine.java:64-71`

```java
byte[] bodyBytes = clientResponse.getBody().readAllBytes();       // ← 先全读入堆
long maxSize = 10 * 1024 * 1024L;
if (bodyBytes.length > maxSize) {                                 // ← 后检查
    debugResponse.setBody("[响应体超过10MB限制，已截断]");
```

**风险**：`readAllBytes()` 在内存检查之前执行。攻击者或配置错误的服务器返回数 GB 响应时，JVM 会在 10MB 截断逻辑到达之前直接 OOM。

**复现条件**：API 调试功能请求一个返回大文件（ISO/视频）的 URL。

**修复建议**：

```java
// 方式一：用有限读取 + 超额检测
InputStream is = clientResponse.getBody();
byte[] bodyBytes = is.readNBytes(MAX_SIZE + 1); // 最多读 MAX_SIZE+1 字节
if (bodyBytes.length > MAX_SIZE) {
    // 截断处理
}

// 方式二：用 raw 流逐块读取并提前终止
byte[] buf = new byte[8192];
int total = 0;
ByteArrayOutputStream baos = new ByteArrayOutputStream();
while ((n = is.read(buf)) != -1) {
    total += n;
    if (total > MAX_SIZE) {
        baos.write(buf, 0, n);
        break; // 或继续读完但不写入
    }
    baos.write(buf, 0, n);
}
```

---

### 4. `ImageSessionService` — 会话 Map 无硬容量上限 → 堆内存泄漏

**文件**：`backend/image-core/src/main/java/com/hfwas/devops/image/service/ImageSessionService.java:51`

```java
private final Map<String, ImageSession> sessions = new ConcurrentHashMap<>();
```

每上传一个图片文件创建一个 `ImageSession`（含文件路径、预览图路径、元数据对象），仅靠 30s TTL 驱逐。没有兜底的硬限制。

**风险**：持续上传文件且不访问（TTL 不刷新），session 累积。每个 session 平均 ~2KB 元数据 + 指向图片文件的 Path 引用（文件不释放）。

**修复建议**：
- 设置 `maxSessions` 拒绝超限请求
- 或用 Caffeine Cache `maximumSize(500).expireAfterWrite(ttl)`
- 上传时同时检查磁盘空间

---

## 🟠 中危

### 5. `PodExecWebSocketHandler` — 调度器线程被长任务阻塞

**文件**：`backend/server/src/main/java/com/hfwas/devops/ws/PodExecWebSocketHandler.java:56-60, 247-260, 316-329`

```java
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, ...);
...
scheduler.submit(() -> {                               // ← 异步等待占用调度线程
    waitForContainerReady(client, namespace, pod, container, EPHEMERAL_WAIT_MS);
    ...
});
```

**风险**：`waitForContainerReady()` / `waitForPodReady()` 在**唯一的** 2 线程 scheduler 中执行 `Thread.sleep(2000)` 轮询。多个调试连接会占满这 2 个线程，导致：
- `checkIdleSessions()` 无法执行 → Ping/Pong 检测失效 → 连接泄漏
- 新连接无法处理

**修复建议**：
- 异步等待任务使用独立的线程池（如 `newCachedThreadPool` 但有界）
- 或用 `CompletableFuture.supplyAsync(() -> waitForPod(...), independentPool)`
- scheduler 仅保留定时任务

---

### 6. `OcrPythonWorker.readLine()` — 20ms 精细轮询

**文件**：`backend/file-parser/src/main/java/com/hfwas/devops/fileparser/ocr/OcrPythonWorker.java:200-217`

```java
while (System.nanoTime() < deadline) {
    if (!process.isAlive() && !stdout.ready()) return null;
    if (stdout.ready()) return stdout.readLine();
    try {
        Thread.sleep(20);
    } catch (InterruptedException e) { ... }
}
```

**风险**：每 20ms 轮询一次 Python 子进程 stdout。5 并发 × 每次识别 ~2s = 每个请求 ~100 次唤醒。`Thread.sleep(20)` 本身不占 CPU，但线程上下文切换随着并发数线性增长。

**修复建议**：
- **推荐**：改用阻塞 `process.getInputStream().readLine()`，省去轮询（由 OS 调度）
- 或增加 sleep 间隔到 100-200ms

---

### 7. `PodExecWebSocketHandler.startReaderThread()` — K8s 输出忙等

**文件**：`backend/server/src/main/java/com/hfwas/devops/ws/PodExecWebSocketHandler.java:388-438`

```java
while (wsSession.isOpen()) {
    boolean hasOutput = false;
    if (stdout != null && stdout.available() > 0) {       // 非阻塞检查
        n = stdout.read(buf);
        ...
    }
    if (stderr != null && stderr.available() > 0) { ... }
    if (!hasOutput) {
        Thread.sleep(50);                                  // 无输出兜底
    }
}
```

**风险**：持续有输出时（如 `tail -f`、持续编译输出），循环以最高速运行：读 → 发 → `available()` → 读 → 发... 无休眠间隙。

**比较**：同一仓库的 `PodShellWebSocketHandler.readStream()` 使用 `stream.read(buf)` 阻塞读取（正确处理方式），此处模式不一致。

**修复建议**：
- 合并 stdout/stderr 到单个阻塞读线程
- 使用 `InputStream.read(buf)` 阻塞读取 + SO_TIMEOUT

---

### 8. `PodShellWebSocketHandler` — 每个会话 2 个实体线程

**文件**：`backend/container-core/src/main/java/com/hfwas/devops/container/ws/PodShellWebSocketHandler.java:194-201`

```java
Thread stdoutReader = new Thread(...);
Thread stderrReader = new Thread(...);
```

每个 shell 会话创建 2 个独立的 OS 读取线程。50 个并发 shell 会话 = 100 个线程。

虽然有空闲超时回收，但峰值负载下线程数 = `2 × (pod-shell + pod-exec) session count`，可轻易达到数百。

**修复建议**：
- 添加最大并发会话数限制（当前只检查了 `PodExecWebSocketHandler.MAX_SESSIONS=20`，但 `PodShellWebSocketHandler` 无限）
- 或改用虚拟线程（Java 21+）

---

### 9. `ClusterHeartbeatJob` — 顺序遍历所有集群

**文件**：`backend/container-core/src/main/java/com/hfwas/devops/container/service/cluster/ClusterHeartbeatJob.java:25-52`

```java
@Scheduled(fixedRate = 60_000)
public void heartbeat() {
    List<ClusterEntity> clusters = clusterMapper.selectList(null);
    for (ClusterEntity cluster : clusters) {       // 顺序
        KubernetesClient client = clientFactory.getClient(cluster);
        healthy = client.getKubernetesVersion() != null;
    }
}
```

**风险**：10 个集群中若 3 个不可达（TCP 超时 30s），单次 heartbeat 耗时 90s+，后续调度被跳过。集群规模增长时影响逐级放大。

**修复建议**：

```java
CompletableFuture.allOf(
    clusters.stream()
        .map(c -> CompletableFuture.runAsync(() -> checkCluster(c), heartbeatPool)
            .orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(e -> { log.warn("cluster {} check failed", c.getId()); return null; }))
        .toArray(CompletableFuture[]::new)
).join();
```

---

### 10. `OcrPreprocessor` — Pixel-by-pixel `getRGB/setRGB` CPU 密集

**文件**：`backend/file-parser/src/main/java/com/hfwas/devops/fileparser/ocr/OcrPreprocessor.java:204-241`

```java
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        int rgb = image.getRGB(x, y);      // 每像素色彩空间转换
        ...
        result.setRGB(x, y, outRgb);
    }
}
```

**风险**：`getRGB()` / `setRGB()` 每次调用经过色彩空间转换和边界检查。1200×900 图片 = 1.08M 次调用/遍。直方图计算 + 对比度拉伸 = 2 遍全像素遍历 = 2.16M 次调用。

5 并发 OCR 请求下 = 10M+ 次 `getRGB()`/s。

**修复建议**：

```java
// 用 Raster 批量操作替代逐像素
int[] pixels = image.getRaster().getPixels(0, 0, width, height, (int[]) null);
for (int i = 0; i < pixels.length; i++) {
    pixels[i] = stretch(pixels[i], minGray, maxGray);
}
result.getRaster().setPixels(0, 0, width, height, pixels);
```

---

## 🟢 低危 / 最佳实践建议

| # | 位置 | 问题 | 风险 | 建议 |
|---|------|------|------|------|
| 11 | `MonitorService` | 单次监控页面刷新发起 ~20 次独立的 HTTP 范围查询 | Prometheus 瞬时 CPU/连接压力 | 合并 PromQL 查询（`sum by()` 替代多个 query）、加 30s 缓存 |
| 12 | `TikaDocumentParser` | `Files.readString()` 全量加载解析文本到堆 | 并发解析大文档堆压力大 | 已有 `maxTextLength` 限制但可降低默认值 |
| 13 | `ScriptSandbox` | 每次脚本执行创建新 GraalVM Context | Context 构建开销（~50-200ms 首次编译） | 使用 Context 池复用（参考 GraalVM 多线程模式） |
| 14 | `ImageIoTransformer.subsampleFactor` | 同一文件打开 2 次 `ImageInputStream` | 重复文件 IO | 合并为一次读取，`ImageReader` 可复用 input |
| 15 | `RegistryHeartbeatJob` | 5 分钟一轮，HTTP client 无显式超时 | 注册中心不可达时线程阻塞 | 为 `RestTemplate` / `WebClient` 设置 connect/read timeout |

---

## 📊 对外内存 (Off-Heap) 专项评估

| 模块 | 对外内存来源 | 控制措施 | 风险评估 |
|------|-------------|---------|---------|
| **OcrService (RapidOCR)** | ONNX Runtime 模型加载 + 推理 native 内存 | ✅ Semaphore(maxConcurrent=配置) | 🟡 需确认默认值 ≤ 2-3 |
| **ImageMagickTransformer** | ImageMagick 子进程堆外像素缓存 | ✅ `-limit memory 256MiB -limit map 512MiB` + ImageWorkLimiter | 🟢 控制良好 |
| **ImageIoTransformer** | BufferedImage on-heap（堆内） | ✅ ImageWorkLimiter 限制并发 | 🟢 受 -Xmx 控制 |
| **DirectByteBuffer** | 未发现显式 `ByteBuffer.allocateDirect()` | — | 🟢 无风险 |
| **线程栈 (LogTailHandler)** | OS 线程栈内存 ~1MB/线程 | ❌ 无界 `newCachedThreadPool` | 🔴 见#1 |
| **线程栈 (TektonPipelineExecutor)** | OS 线程栈内存 ~1MB/线程 | ❌ 无界 `newCachedThreadPool` | 🔴 见#2 |
| **Tika 内置解析器** | Native 库加载（PDFBox 等） | 进程内单例，不释放 | 🟡 持久占用量可控 |

**核心结论**：没有发现 `DirectByteBuffer` 泄漏或 JNI `Unsafe` 滥用。对外内存的主要风险来自 **无界线程池** 和 **ONNX Runtime native memory**。

---

## 📋 行动建议优先级

| 优先级 | 事项 | 对应# | 工作量 |
|--------|------|-------|--------|
| **P0** | 修复 LogTailHandler 无界线程池 | #1 | 小（~10 行改动） |
| **P0** | 修复 TektonPipelineExecutor 无界线程池 | #2 | 中（~30 行） |
| **P0** | 修复 HttpDebugEngine readAllBytes OOM | #3 | 小（~15 行） |
| **P0** | ImageSessionService 加最大会话数 | #4 | 小（~5 行） |
| **P1** | 分离 PodExecWebSocketHandler 异步等待线程池 | #5 | 中（~20 行） |
| **P1** | PodShellWebSocketHandler 加最大会话限制 | #8 | 小（~5 行） |
| **P1** | 并行化 ClusterHeartbeatJob | #9 | 小（~20 行） |
| **P1** | PodExecWebSocketHandler 读线程改用阻塞模式 | #7 | 中（~30 行） |
| **P2** | OcrPreprocessor 改用 Raster 批量像素操作 | #10 | 小（~15 行） |
| **P2** | OcrPythonWorker 改用阻塞 readLine | #6 | 小（~10 行） |
| **P2** | MonitorService 加查询缓存 | #11 | 中（~40 行） |
| **P3** | ScriptSandbox GraalVM Context 池化 | #13 | 中 |
| **P3** | RegistryHeartbeatJob 加超时 | #15 | 小 |

---

## 验证方法

1. **线程池问题 (#1, #2)**：启动后压测 WebSocket 连接，`jstack` 检查线程数
2. **OOM 问题 (#3)**：调用 API 调试接口请求一个大文件 URL，检查堆使用
3. **会话泄漏 (#4, #8, #13)**：持续发起请求后检查 `/actuator/health` 和 `jmap -histo`
4. **对外内存 (#1, #2, OcrService)**：监控 `jstat -gcutil` + NMT (`-XX:NativeMemoryTracking=summary`)
5. **CPU 热点 (#10)**：用 `async-profiler` 火焰图验证像素操作热点