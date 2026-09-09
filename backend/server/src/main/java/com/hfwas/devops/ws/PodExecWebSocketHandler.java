package com.hfwas.devops.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PodExecWebSocketHandler extends AbstractWebSocketHandler {

    private static final int MAX_SESSIONS = 20;
    private static final long IDLE_TIMEOUT_MS = 1_800_000; // 30 min
    private static final long PING_INTERVAL_MS = 30_000;   // 30s
    private static final long PONG_TIMEOUT_MS = 90_000;    // 90s
    private static final long EPHEMERAL_WAIT_MS = 120_000; // 2 min
    private static final long DEBUG_POD_WAIT_MS = 180_000; // 3 min

    private static final String DEBUG_IMAGE = "nicolaka/netshoot:latest";
    private static final String WORKSPACE_PATH = "/workspace/source/src";
    private static final String EPHEMERAL_CONTAINER_PREFIX = "debug-";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger(0);

    private final PipelineDefinitionService definitionService;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final ObjectProvider<KubernetesClient> kubernetesClients;

    // 活跃会话: sessionId -> PodExecSession
    private final ConcurrentHashMap<String, PodExecSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "pod-exec-ping");
        t.setDaemon(true);
        return t;
    });

    public PodExecWebSocketHandler(PipelineDefinitionService definitionService,
                                   PipelineRunMapper runMapper,
                                   PipelineRunJobMapper runJobMapper,
                                   ObjectProvider<KubernetesClient> kubernetesClients) {
        this.definitionService = definitionService;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.kubernetesClients = kubernetesClients;

        // 启动全局 ping/pong 检测定时任务
        scheduler.scheduleAtFixedRate(this::checkIdleSessions, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (sessions.size() >= MAX_SESSIONS) {
            sendErrorAndClose(session, "服务器负载已达上限，请稍后重试");
            return;
        }

        KubernetesClient client = kubernetesClients.getIfAvailable();
        if (client == null) {
            sendErrorAndClose(session, "未配置执行集群");
            return;
        }

        Map<String, String> pathVars = extractPathVariables(session);
        String pipelineId = pathVars.get("pipelineId");
        String runId = pathVars.get("runId");
        String jobId = pathVars.get("jobId");

        if (pipelineId == null || runId == null || jobId == null) {
            sendErrorAndClose(session, "请求路径不完整");
            return;
        }

        String userId = (String) session.getAttributes().get("userId");

        try {
            // 验证权限
            definitionService.requireOwned(Long.valueOf(pipelineId));

            PipelineRunEntity run = runMapper.selectById(Long.valueOf(runId));
            if (run == null || !Long.valueOf(pipelineId).equals(run.getPipelineId())) {
                sendErrorAndClose(session, "运行记录不存在");
                return;
            }

            PipelineRunJobEntity job = runJobMapper.selectById(Long.valueOf(jobId));
            if (job == null || !Long.valueOf(runId).equals(job.getRunId())) {
                sendErrorAndClose(session, "任务记录不存在");
                return;
            }

            // 确定连接模式并连接
            String sessionId = session.getId();
            String podName = job.getPodName();
            String namespace = job.getNamespace();
            String containerName = resolveContainerName(session);

            if (podName == null || namespace == null) {
                sendErrorAndClose(session, "Pod 尚未分配，请等待任务调度");
                return;
            }

            connect(session, client, namespace, podName, containerName, run);

        } catch (NumberFormatException e) {
            sendErrorAndClose(session, "路径参数格式错误");
        } catch (Exception e) {
            log.error("WebSocket 建立连接异常: {}", e.getMessage(), e);
            sendErrorAndClose(session, "连接失败: " + e.getMessage());
        }
    }

    /**
     * 按 4 种模式连接 Pod
     */
    private void connect(WebSocketSession session, KubernetesClient client,
                         String namespace, String podName, String containerName,
                         PipelineRunEntity run) {
        // 1. 检查 Pod 是否存在
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();

        if (pod != null && pod.getStatus() != null) {
            // Pod 存在 — 检查容器状态
            ContainerState containerState = findContainerState(pod, containerName);

            if (containerState != null && containerState.getRunning() != null) {
                // Mode 1: exec — 容器正在运行
                doExec(session, client, namespace, podName, containerName);
            } else {
                // Mode 2: ephemeral — 容器已退出或等待中，注入调试容器
                doEphemeral(session, client, namespace, podName, containerName);
            }
        } else {
            // Pod 已删除 — 检查 workspace
            String tektonName = run.getTektonName();
            boolean isTaskMode = isTaskMode(run);
            if (isTaskMode) {
                sendErrorAndClose(session, "串行流水线使用 emptyDir，Pod 删除后现场已丢失，请查看日志");
                return;
            }

            // Mode 3: debug_pod — 检查 PVC
            String pvcName = tektonName != null ? tektonName + "-ws" : null;
            if (pvcName != null) {
                PersistentVolumeClaim pvc = client.persistentVolumeClaims()
                        .inNamespace(namespace).withName(pvcName).get();
                if (pvc != null) {
                    doDebugPod(session, client, namespace, podName, containerName, pvcName);
                    return;
                }
            }
            // Mode 4: unavailable
            sendErrorAndClose(session, "工作区 PVC 已不存在，现场已丢失，请查看日志");
        }
    }

    // ==================== Mode 1: exec ====================

    private void doExec(WebSocketSession session, KubernetesClient client,
                        String namespace, String podName, String containerName) {
        try {
            String[] cmd = {"sh", "-c", "export TERM=xterm-256color; exec bash 2>/dev/null || exec sh"};

            ExecWatch watch = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .redirectingInput()
                    .redirectingOutput()
                    .redirectingError()
                    .withTTY()
                    .exec(cmd);

            PodExecSession execSession = new PodExecSession(session, watch, containerName, "exec");
            sessions.put(session.getId(), execSession);

            // 启动读线程：将 K8s 输出转发到 WebSocket
            startReaderThread(execSession);

            // 通知前端连接成功
            sendJson(session, Map.of("type", "connected", "mode", "exec", "container", containerName));

            log.info("exec 模式连接成功: session={}, pod={}/{} container={}",
                    session.getId(), namespace, podName, containerName);

        } catch (Exception e) {
            log.error("exec 模式连接失败: {}", e.getMessage(), e);
            sendErrorAndClose(session, "exec 连接失败: " + e.getMessage());
        }
    }

    // ==================== Mode 2: ephemeral ====================

    private void doEphemeral(WebSocketSession session, KubernetesClient client,
                             String namespace, String podName, String targetContainer) {
        String debugContainerName = EPHEMERAL_CONTAINER_PREFIX + targetContainer;

        try {
            // 注入 ephemeral container
            EphemeralContainer ec = new EphemeralContainerBuilder()
                    .withName(debugContainerName)
                    .withImage(DEBUG_IMAGE)
                    .withCommand("sleep", "86400")
                    .withStdin(true)
                    .withTty(true)
                    .withTargetContainerName(targetContainer)
                    .build();

            Pod currentPod = client.pods().inNamespace(namespace).withName(podName).get();
            if (currentPod == null) {
                sendErrorAndClose(session, "Pod 已不存在");
                return;
            }

            // 检查是否已有同名 debug 容器
            if (!hasEphemeralContainer(currentPod, debugContainerName)) {
                currentPod.getSpec().getEphemeralContainers().add(ec);
                client.pods().inNamespace(namespace).resource(currentPod).replace();
            }

            sendJson(session, Map.of("type", "progress", "message", "正在注入调试容器..."));

            // 异步等待 ephemeral container 就绪
            String finalDebugContainer = debugContainerName;
            scheduler.submit(() -> {
                try {
                    waitForContainerReady(client, namespace, podName, finalDebugContainer, EPHEMERAL_WAIT_MS);
                    if (session.isOpen()) {
                        doExec(session, client, namespace, podName, finalDebugContainer);
                    }
                } catch (Exception e) {
                    log.error("ephemeral 容器就绪等待超时: {}", e.getMessage());
                    if (session.isOpen()) {
                        sendErrorAndClose(session, "调试容器启动超时: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            log.error("ephemeral 容器注入失败: {}", e.getMessage(), e);
            sendErrorAndClose(session, "调试容器注入失败: " + e.getMessage());
        }
    }

    // ==================== Mode 3: debug_pod ====================

    private void doDebugPod(WebSocketSession session, KubernetesClient client,
                            String namespace, String originalPodName,
                            String containerName, String pvcName) {
        String debugPodName = "debug-" + originalPodName + "-" + Instant.now().toEpochMilli();

        try {
            // 创建调试 Pod
            Pod debugPod = new PodBuilder()
                    .withNewMetadata()
                    .withName(debugPodName)
                    .withNamespace(namespace)
                    .withLabels(Map.of(
                            "app", "pipeline-debug",
                            "pipeline-debug-for", originalPodName
                    ))
                    .endMetadata()
                    .withNewSpec()
                    .withRestartPolicy("Never")
                    .withContainers(new ContainerBuilder()
                            .withName(containerName)
                            .withImage(DEBUG_IMAGE)
                            .withCommand("sleep", "86400")
                            .withStdin(true)
                            .withTty(true)
                            .withVolumeMounts(new VolumeMountBuilder()
                                    .withName("workspace")
                                    .withMountPath(WORKSPACE_PATH)
                                    .build())
                            .build())
                    .withVolumes(new VolumeBuilder()
                            .withName("workspace")
                            .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                    .withClaimName(pvcName)
                                    .withReadOnly(false)
                                    .build())
                            .build())
                    .endSpec()
                    .build();

            client.pods().inNamespace(namespace).resource(debugPod).create();

            // 记录创建的 debug Pod（用于 GC）
            recordDebugPod(originalPodName, debugPodName, namespace);

            sendJson(session, Map.of("type", "progress", "message", "正在创建调试 Pod..."));

            // 异步等待 Pod 就绪
            scheduler.submit(() -> {
                try {
                    waitForPodReady(client, namespace, debugPodName, DEBUG_POD_WAIT_MS);
                    if (session.isOpen()) {
                        doExec(session, client, namespace, debugPodName, containerName);
                    }
                } catch (Exception e) {
                    log.error("调试 Pod 就绪等待超时: {}", e.getMessage());
                    if (session.isOpen()) {
                        sendErrorAndClose(session, "调试 Pod 启动超时: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            log.error("调试 Pod 创建失败: {}", e.getMessage(), e);
            sendErrorAndClose(session, "调试 Pod 创建失败: " + e.getMessage());
        }
    }

    // ==================== 消息处理 ====================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        PodExecSession execSession = sessions.get(session.getId());
        if (execSession == null) return;

        try {
            JsonNode json = MAPPER.readTree(message.getPayload());
            String type = json.path("type").asText("");

            switch (type) {
                case "input" -> {
                    String data = json.path("data").asText();
                    if (execSession.execInput != null && data != null) {
                        execSession.execInput.write(data.getBytes());
                        execSession.execInput.flush();
                    }
                    execSession.lastActivity = System.currentTimeMillis();
                }
                case "resize" -> {
                    int cols = json.path("cols").asInt(80);
                    int rows = json.path("rows").asInt(24);
                    if (execSession.execWatch != null) {
                        execSession.execWatch.resize(cols, rows);
                    }
                }
                case "pong" -> {
                    execSession.lastPong = System.currentTimeMillis();
                    execSession.lastActivity = System.currentTimeMillis();
                }
                default -> log.debug("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("处理 WebSocket 消息异常: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输错误: session={}, error={}", session.getId(), exception.getMessage());
        closeSession(session.getId());
    }

    // ==================== 内部方法 ====================

    private void startReaderThread(PodExecSession execSession) {
        Thread reader = new Thread(() -> {
            InputStream stdout = execSession.execWatch.getOutput();
            InputStream stderr = execSession.execWatch.getError();
            WebSocketSession wsSession = execSession.wsSession;

            byte[] buf = new byte[8192];
            try {
                while (wsSession.isOpen()) {
                    boolean hasOutput = false;

                    if (stdout != null && stdout.available() > 0) {
                        int n = stdout.read(buf);
                        if (n > 0) {
                            wsSession.sendMessage(new BinaryMessage(buf, 0, n, true));
                            hasOutput = true;
                        } else if (n == -1) {
                            break;
                        }
                    }

                    if (stderr != null && stderr.available() > 0) {
                        int n = stderr.read(buf);
                        if (n > 0) {
                            wsSession.sendMessage(new BinaryMessage(buf, 0, n, true));
                            hasOutput = true;
                        } else if (n == -1) {
                            break;
                        }
                    }

                    if (hasOutput) {
                        execSession.lastActivity = System.currentTimeMillis();
                    } else {
                        // 无输出时短暂休眠避免忙等
                        Thread.sleep(50);
                    }
                }
            } catch (IOException e) {
                if (!"Broken pipe".equals(e.getMessage()) && !"Stream closed".equals(e.getMessage())) {
                    log.debug("reader 线程 IO 异常: {}", e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                closeSession(execSession.wsSession.getId());
            }
        }, "k8s-exec-reader-" + execSession.wsSession.getId().substring(0, 8));

        reader.setDaemon(true);
        reader.start();
    }

    private void closeSession(String sessionId) {
        PodExecSession execSession = sessions.remove(sessionId);
        if (execSession == null) return;

        try {
            if (execSession.execInput != null) {
                execSession.execInput.close();
            }
        } catch (Exception ignored) {}

        try {
            if (execSession.execWatch != null) {
                execSession.execWatch.close();
            }
        } catch (Exception ignored) {}

        try {
            if (execSession.wsSession.isOpen()) {
                execSession.wsSession.close(CloseStatus.NORMAL);
            }
        } catch (Exception ignored) {}

        log.debug("会话已关闭: {}", sessionId);
    }

    private void sendJson(WebSocketSession session, Map<String, ?> data) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(data)));
            }
        } catch (IOException e) {
            log.warn("发送 JSON 消息失败: {}", e.getMessage());
        }
    }

    private void sendErrorAndClose(WebSocketSession session, String message) {
        try {
            sendJson(session, Map.of("type", "error", "message", message));
            if (session.isOpen()) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason(message));
            }
        } catch (IOException ignored) {}
    }

    private Map<String, String> extractPathVariables(WebSocketSession session) {
        // URI 格式: /ws/exec/{pipelineId}/{runId}/{jobId}
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        // ["", "ws", "exec", pipelineId, runId, jobId]
        if (parts.length >= 6) {
            return Map.of(
                    "pipelineId", parts[3],
                    "runId", parts[4],
                    "jobId", parts[5]
            );
        }
        return Map.of();
    }

    private String resolveContainerName(WebSocketSession session) {
        // 从 query param container=xxx 获取容器名
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if ("container".equals(parts[0]) && parts.length == 2 && !parts[1].isEmpty()) {
                    return java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private ContainerState findContainerState(Pod pod, String containerName) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return null;
        for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
            if (cs.getName().equals(containerName)) {
                return cs.getState();
            }
            // 如果 containerName 为 null，取第一个
            if (containerName == null) {
                return cs.getState();
            }
        }
        return null;
    }

    private boolean hasEphemeralContainer(Pod pod, String name) {
        if (pod.getSpec() == null || pod.getSpec().getEphemeralContainers() == null) return false;
        return pod.getSpec().getEphemeralContainers().stream()
                .anyMatch(ec -> name.equals(ec.getName()));
    }

    /**
     * 尝试找到 Pod 中第一个 step- 前缀的容器名，若找不到返回 null
     */
    private String findFirstStepContainer(Pod pod) {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) return null;
        return pod.getSpec().getContainers().stream()
                .filter(c -> c.getName().startsWith("step-"))
                .findFirst()
                .map(Container::getName)
                .orElse(null);
    }

    /**
     * 等待容器就绪（用于 ephemeral container 启动等待）
     */
    private void waitForContainerReady(KubernetesClient client, String namespace,
                                       String podName, String containerName, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod != null && pod.getStatus() != null) {
                for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                    if (containerName.equals(cs.getName())
                            && cs.getState() != null
                            && cs.getState().getRunning() != null) {
                        return; // 就绪
                    }
                }
            }
            Thread.sleep(2000);
        }
        throw new TimeoutException("容器 " + containerName + " 在 " + (timeoutMs / 1000) + "s 内未就绪");
    }

    /**
     * 等待 Pod 就绪（用于 debug Pod 启动等待）
     */
    private void waitForPodReady(KubernetesClient client, String namespace,
                                 String podName, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod != null && pod.getStatus() != null) {
                String phase = pod.getStatus().getPhase();
                if ("Running".equals(phase)) {
                    return;
                }
                if ("Failed".equals(phase) || "Succeeded".equals(phase)) {
                    throw new RuntimeException("Pod 状态异常: " + phase);
                }
            }
            Thread.sleep(2000);
        }
        throw new TimeoutException("Pod " + podName + " 在 " + (timeoutMs / 1000) + "s 内未就绪");
    }

    private void checkIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(execSession -> {
            // 发送 ping
            if (execSession.wsSession.isOpen()) {
                sendJson(execSession.wsSession, Map.of("type", "ping"));
            }

            // 检查是否超时
            boolean idleTimeout = (now - execSession.lastActivity) > IDLE_TIMEOUT_MS;
            boolean pongTimeout = (now - execSession.lastPong) > PONG_TIMEOUT_MS
                    && (now - execSession.lastActivity) > PING_INTERVAL_MS * 3;

            if (pongTimeout) {
                log.warn("Pong 超时，关闭会话: {}", execSession.wsSession.getId());
                closeSession(execSession.wsSession.getId());
                return true;
            }

            if (idleTimeout) {
                sendJson(execSession.wsSession, Map.of("type", "error", "message", "连接空闲超时"));
                closeSession(execSession.wsSession.getId());
                return true;
            }

            return false;
        });
    }

    private void recordDebugPod(String originalPodName, String debugPodName, String namespace) {
        // 记录 debug Pod 信息，供 PipelineDebugPodGc 清理
        // 实现方式可以是内存 Map 或写入 DB；这里用内存记录
        log.info("debug Pod 已创建: originalPod={}, debugPod={}/{}", originalPodName, namespace, debugPodName);
    }

    private static boolean isTaskMode(PipelineRunEntity run) {
        // TASK 模式：多 job 共享 Pod，由 segment 调度
        return run.getSegmentIndex() != null && run.getSegmentIndex() > 0
                && run.getTektonName() != null && run.getTektonName().startsWith("task-");
    }

    // ==================== 内部类 ====================

    private static class PodExecSession {
        final WebSocketSession wsSession;
        final ExecWatch execWatch;
        final OutputStream execInput;
        final String containerName;
        final String mode;
        volatile long lastActivity;
        volatile long lastPong;

        PodExecSession(WebSocketSession wsSession, ExecWatch execWatch,
                       String containerName, String mode) {
            this.wsSession = wsSession;
            this.execWatch = execWatch;
            this.execInput = execWatch.getInput();
            this.containerName = containerName;
            this.mode = mode;
            this.lastActivity = System.currentTimeMillis();
            this.lastPong = System.currentTimeMillis();
        }
    }

    /**
     * 获取当前活跃连接数（供监控使用）
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}