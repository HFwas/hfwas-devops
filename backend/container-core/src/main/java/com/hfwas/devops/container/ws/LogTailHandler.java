package com.hfwas.devops.container.ws;

import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket handler for real-time Pod log tailing.
 * Connection URL: WS /ws/container/logs/{clusterId}?namespace=default&pod=xxx&container=xxx
 */
@Slf4j
@Component
public class LogTailHandler extends AbstractWebSocketHandler {

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ConcurrentHashMap<String, LogWatch> activeLogs = new ConcurrentHashMap<>();
    private final ExecutorService logExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "log-tail-");
        t.setDaemon(true);
        return t;
    });

    public LogTailHandler(ClusterService clusterService,
                          ClusterKubernetesClientFactory clientFactory) {
        this.clusterService = clusterService;
        this.clientFactory = clientFactory;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) {
                sendErrorAndClose(session, "URI 无效");
                return;
            }

            // Parse path: /ws/container/logs/{clusterId}
            String[] segments = uri.getPath().split("/");
            if (segments.length < 5) {
                sendErrorAndClose(session, "路径不完整: /ws/container/logs/{clusterId}");
                return;
            }
            Long clusterId = Long.parseLong(segments[4]);

            // Parse query params
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String namespace = queryParams.get("namespace");
            String podName = queryParams.get("pod");
            String container = queryParams.get("container");

            if (namespace == null || podName == null) {
                sendErrorAndClose(session, "缺少必要参数: namespace, pod");
                return;
            }

            // Tenant check
            String userId = (String) session.getAttributes().get("userId");
            if (userId == null) {
                sendErrorAndClose(session, "未认证");
                return;
            }

            // Get client
            var entity = clusterService.getByIdInternal(clusterId);
            KubernetesClient client = clientFactory.getClient(entity);

            // Start log tail
            LogWatch logWatch;
            if (container != null && !container.isBlank()) {
                logWatch = client.pods().inNamespace(namespace).withName(podName)
                        .inContainer(container).tailingLines(100).watchLog();
            } else {
                logWatch = client.pods().inNamespace(namespace).withName(podName)
                        .tailingLines(100).watchLog();
            }

            activeLogs.put(session.getId(), logWatch);

            // Read logs in background thread
            logExecutor.submit(() -> {
                try (InputStream is = logWatch.getOutput()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while (session.isOpen() && (n = is.read(buf)) != -1) {
                        if (n > 0) {
                            session.sendMessage(new BinaryMessage(buf, 0, n, true));
                        }
                    }
                } catch (Exception e) {
                    if (session.isOpen()) {
                        log.debug("Log tail stream ended: {}", e.getMessage());
                    }
                } finally {
                    closeSession(session.getId());
                }
            });

        } catch (Exception e) {
            log.error("Log tail connection error: {}", e.getMessage(), e);
            sendErrorAndClose(session, "连接失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Log tail WS error: {}", exception.getMessage());
        closeSession(session.getId());
    }

    private void closeSession(String sessionId) {
        LogWatch watch = activeLogs.remove(sessionId);
        if (watch != null) {
            try { watch.close(); } catch (Exception ignored) {}
        }
    }

    private void sendErrorAndClose(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason(message));
            }
        } catch (Exception ignored) {}
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2) {
                params.put(parts[0], parts[1]);
            }
        }
        return params;
    }
}