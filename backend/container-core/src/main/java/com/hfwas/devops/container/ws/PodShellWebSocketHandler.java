package com.hfwas.devops.container.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.service.SecurityHelper;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for interactive pod shell (kubectl exec equivalent).
 * URI: /ws/container/shell/{clusterId}/{namespace}/{podName}?container=xxx
 */
@Slf4j
@Component
public class PodShellWebSocketHandler extends AbstractWebSocketHandler {

    private static final long IDLE_TIMEOUT_MS = 1_800_000; // 30 min
    private static final long PING_INTERVAL_MS = 30_000;
    private static final long PONG_TIMEOUT_MS = 90_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ConcurrentHashMap<String, ShellSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "pod-shell-ping");
        t.setDaemon(true);
        return t;
    });

    public PodShellWebSocketHandler(ClusterService clusterService,
                                    ClusterKubernetesClientFactory clientFactory) {
        this.clusterService = clusterService;
        this.clientFactory = clientFactory;
        scheduler.scheduleAtFixedRate(this::checkIdleSessions, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            Map<String, String> pathVars = extractPathVariables(session);
            String clusterIdStr = pathVars.get("clusterId");
            String namespace = pathVars.get("namespace");
            String podName = pathVars.get("podName");

            if (clusterIdStr == null || namespace == null || podName == null) {
                sendErrorAndClose(session, "请求路径不完整，需要 /ws/container/shell/{clusterId}/{namespace}/{podName}");
                return;
            }

            long clusterId = Long.parseLong(clusterIdStr);
            String containerName = resolveContainerName(session);

            // Verify cluster access
            Long tenantId = SecurityHelper.currentTenantId();
            ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
            KubernetesClient client = clientFactory.getClient(cluster);

            // Verify pod and container exist
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                sendErrorAndClose(session, "Pod 不存在: " + podName);
                return;
            }

            if (containerName == null) {
                // Default to first running container
                containerName = findFirstRunningContainer(pod);
            }
            if (containerName == null) {
                sendErrorAndClose(session, "未找到可用的容器");
                return;
            }

            doExec(session, client, namespace, podName, containerName);

        } catch (NumberFormatException e) {
            sendErrorAndClose(session, "集群 ID 格式错误");
        } catch (Exception e) {
            log.error("Pod shell 连接异常: {}", e.getMessage(), e);
            sendErrorAndClose(session, "连接失败: " + e.getMessage());
        }
    }

    private void doExec(WebSocketSession session, KubernetesClient client,
                        String namespace, String podName, String containerName) {
        try {
            String[] cmd = {"sh"};

            ExecWatch watch = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .redirectingInput()
                    .redirectingOutput()
                    .redirectingError()
                    .withTTY()
                    .exec(cmd);

            ShellSession shellSession = new ShellSession(session, watch, containerName);
            sessions.put(session.getId(), shellSession);

            // Start reader thread: K8s output -> WebSocket
            startReaderThread(shellSession);

            // Notify frontend
            sendJson(session, Map.of("type", "connected", "container", containerName));

            log.info("Pod shell connected: session={}, pod={}/{} container={}",
                    session.getId(), namespace, podName, containerName);

        } catch (Exception e) {
            log.error("Pod shell exec 失败: {}", e.getMessage(), e);
            sendErrorAndClose(session, "exec 连接失败: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ShellSession shellSession = sessions.get(session.getId());
        if (shellSession == null) return;

        try {
            JsonNode json = MAPPER.readTree(message.getPayload());
            String type = json.path("type").asText("");

            switch (type) {
                case "input" -> {
                    String data = json.path("data").asText();
                    log.debug("WS input received: session={}, data='{}'", session.getId(), data);
                    if (shellSession.execInput != null && data != null) {
                        shellSession.execInput.write(data.getBytes());
                        shellSession.execInput.flush();
                        log.debug("WS input written to exec stream: session={}", session.getId());
                    } else {
                        log.warn("WS input skipped: execInput={}, data={}", shellSession.execInput, data);
                    }
                    shellSession.lastActivity = System.currentTimeMillis();
                }
                case "resize" -> {
                    int cols = json.path("cols").asInt(80);
                    int rows = json.path("rows").asInt(24);
                    if (shellSession.execWatch != null) {
                        shellSession.execWatch.resize(cols, rows);
                    }
                }
                case "pong" -> {
                    shellSession.lastPong = System.currentTimeMillis();
                    shellSession.lastActivity = System.currentTimeMillis();
                }
                default -> log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("Handle WS message error: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error: session={}, error={}", session.getId(), exception.getMessage());
        closeSession(session.getId());
    }

    // ==================== Internal ====================

    private void startReaderThread(ShellSession shellSession) {
        Thread stdoutReader = new Thread(() -> readStream("stdout", shellSession.execWatch.getOutput(), shellSession), "k8s-shell-stdout-" + shellSession.wsSession.getId().substring(0, 8));
        Thread stderrReader = new Thread(() -> readStream("stderr", shellSession.execWatch.getError(), shellSession), "k8s-shell-stderr-" + shellSession.wsSession.getId().substring(0, 8));
        stdoutReader.setDaemon(true);
        stderrReader.setDaemon(true);
        stdoutReader.start();
        stderrReader.start();
    }

    private void readStream(String name, InputStream stream, ShellSession shellSession) {
        WebSocketSession wsSession = shellSession.wsSession;
        byte[] buf = new byte[8192];
        try {
            while (wsSession.isOpen()) {
                int n = stream.read(buf);
                if (n > 0) {
                    wsSession.sendMessage(new BinaryMessage(buf, 0, n, true));
                    shellSession.lastActivity = System.currentTimeMillis();
                } else if (n == -1) {
                    break;
                }
            }
        } catch (IOException e) {
            if (!"Broken pipe".equals(e.getMessage()) && !"Stream closed".equals(e.getMessage())) {
                log.debug("{} reader thread IO error: {}", name, e.getMessage());
            }
        } finally {
            log.debug("{} reader thread ended for session {}", name, shellSession.wsSession.getId());
            closeSession(shellSession.wsSession.getId());
        }
    }

    private void closeSession(String sessionId) {
        ShellSession shellSession = sessions.remove(sessionId);
        if (shellSession == null) return;

        try { if (shellSession.execInput != null) shellSession.execInput.close(); } catch (Exception ignored) {}
        try { if (shellSession.execWatch != null) shellSession.execWatch.close(); } catch (Exception ignored) {}
        try { if (shellSession.wsSession.isOpen()) shellSession.wsSession.close(CloseStatus.NORMAL); } catch (Exception ignored) {}

        log.debug("Shell session closed: {}", sessionId);
    }

    private void sendJson(WebSocketSession session, Map<String, ?> data) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(data)));
            }
        } catch (IOException e) {
            log.warn("Send JSON message failed: {}", e.getMessage());
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
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        // URI: /ws/container/shell/{clusterId}/{namespace}/{podName}
        String[] parts = path.split("/");
        // ["", "ws", "container", "shell", clusterId, namespace, podName]
        if (parts.length >= 7) {
            return Map.of(
                    "clusterId", parts[4],
                    "namespace", parts[5],
                    "podName", parts[6]
            );
        }
        return Map.of();
    }

    private String resolveContainerName(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if ("container".equals(parts[0]) && parts.length == 2 && !parts[1].isEmpty()) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private String findFirstRunningContainer(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return null;
        // Prefer the first running container
        for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
            if (cs.getState() != null && cs.getState().getRunning() != null) {
                return cs.getName();
            }
        }
        // Fallback to first container
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null
                && !pod.getSpec().getContainers().isEmpty()) {
            return pod.getSpec().getContainers().getFirst().getName();
        }
        return null;
    }

    private void checkIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(shellSession -> {
            if (shellSession.wsSession.isOpen()) {
                sendJson(shellSession.wsSession, Map.of("type", "ping"));
            }

            boolean idleTimeout = (now - shellSession.lastActivity) > IDLE_TIMEOUT_MS;
            boolean pongTimeout = (now - shellSession.lastPong) > PONG_TIMEOUT_MS
                    && (now - shellSession.lastActivity) > PING_INTERVAL_MS * 3;

            if (pongTimeout) {
                log.warn("Pong timeout, closing session: {}", shellSession.wsSession.getId());
                closeSession(shellSession.wsSession.getId());
                return true;
            }

            if (idleTimeout) {
                sendJson(shellSession.wsSession, Map.of("type", "error", "message", "连接空闲超时"));
                closeSession(shellSession.wsSession.getId());
                return true;
            }

            return false;
        });
    }

    // ==================== Inner class ====================

    private static class ShellSession {
        final WebSocketSession wsSession;
        final ExecWatch execWatch;
        final OutputStream execInput;
        final String containerName;
        volatile long lastActivity;
        volatile long lastPong;

        ShellSession(WebSocketSession wsSession, ExecWatch execWatch, String containerName) {
            this.wsSession = wsSession;
            this.execWatch = execWatch;
            this.execInput = execWatch.getInput();
            this.containerName = containerName;
            this.lastActivity = System.currentTimeMillis();
            this.lastPong = System.currentTimeMillis();
        }
    }
}