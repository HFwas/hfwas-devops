package com.hfwas.devops.container.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for watching K8s resource changes in real-time.
 * Connection URL: WS /ws/container/watch/{clusterId}?resources=Pod,Deployment,Service&namespace=default
 */
@Slf4j
@Component
public class ResourceWatchHandler extends AbstractWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ConcurrentHashMap<String, List<Watch>> sessionWatches = new ConcurrentHashMap<>();

    public ResourceWatchHandler(ClusterService clusterService,
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

            // Parse path for clusterId: /ws/container/watch/{clusterId}
            String[] segments = uri.getPath().split("/");
            if (segments.length < 5) {
                sendErrorAndClose(session, "路径不完整: /ws/container/watch/{clusterId}");
                return;
            }
            Long clusterId = Long.parseLong(segments[4]);

            // Parse query params
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String resourcesParam = queryParams.getOrDefault("resources", "Pod");
            String namespace = queryParams.get("namespace");

            // Tenant check (from handshake attributes)
            String userId = (String) session.getAttributes().get("userId");
            if (userId == null) {
                sendErrorAndClose(session, "未认证");
                return;
            }

            // Get client
            var entity = clusterService.getByIdInternal(clusterId);
            KubernetesClient client = clientFactory.getClient(entity);

            // Parse resource types
            String[] resourceTypes = resourcesParam.split(",");
            List<Watch> watches = new ArrayList<>();

            for (String resource : resourceTypes) {
                resource = resource.trim();
                Watch watch = switch (resource) {
                    case "Pod" -> watchPods(client, namespace, clusterId, session);
                    case "Deployment" -> watchDeployments(client, namespace, clusterId, session);
                    case "Service" -> watchServices(client, namespace, clusterId, session);
                    default -> {
                        log.warn("Unsupported watch resource type: {}", resource);
                        yield null;
                    }
                };
                if (watch != null) {
                    watches.add(watch);
                }
            }

            sessionWatches.put(session.getId(), watches);
            sendJson(session, Map.of("type", "connected", "resources", resourceTypes));

        } catch (Exception e) {
            log.error("ResourceWatch connection error: {}", e.getMessage(), e);
            sendErrorAndClose(session, "连接失败: " + e.getMessage());
        }
    }

    private Watch watchPods(KubernetesClient client, String namespace, Long clusterId, WebSocketSession session) {
        var filter = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();
        return filter.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                if (!session.isOpen()) return;
                try {
                    Map<String, Object> obj = new LinkedHashMap<>();
                    obj.put("type", action.name());
                    obj.put("resource", "Pod");
                    obj.put("object", Map.of(
                            "uid", pod.getMetadata().getUid(),
                            "name", pod.getMetadata().getName(),
                            "namespace", pod.getMetadata().getNamespace(),
                            "status", pod.getStatus() != null ? pod.getStatus().getPhase() : null
                    ));
                    sendJson(session, obj);
                } catch (Exception e) {
                    log.debug("Watch send error: {}", e.getMessage());
                }
            }

            @Override
            public void onClose(WatcherException e) {
                log.debug("Pod watch closed for cluster={}: {}", clusterId, e != null ? e.getMessage() : "normal");
            }
        });
    }

    private Watch watchDeployments(KubernetesClient client, String namespace, Long clusterId, WebSocketSession session) {
        var filter = namespace != null
                ? client.apps().deployments().inNamespace(namespace)
                : client.apps().deployments().inAnyNamespace();
        return filter.watch(new Watcher<io.fabric8.kubernetes.api.model.apps.Deployment>() {
            @Override
            public void eventReceived(Action action, io.fabric8.kubernetes.api.model.apps.Deployment deploy) {
                if (!session.isOpen()) return;
                try {
                    sendJson(session, Map.of(
                            "type", action.name(),
                            "resource", "Deployment",
                            "object", Map.of(
                                    "name", deploy.getMetadata().getName(),
                                    "namespace", deploy.getMetadata().getNamespace()
                            )
                    ));
                } catch (Exception e) {
                    log.debug("Watch send error: {}", e.getMessage());
                }
            }

            @Override
            public void onClose(WatcherException e) {
                log.debug("Deployment watch closed for cluster={}", clusterId);
            }
        });
    }

    private Watch watchServices(KubernetesClient client, String namespace, Long clusterId, WebSocketSession session) {
        var filter = namespace != null
                ? client.services().inNamespace(namespace)
                : client.services().inAnyNamespace();
        return filter.watch(new Watcher<io.fabric8.kubernetes.api.model.Service>() {
            @Override
            public void eventReceived(Action action, io.fabric8.kubernetes.api.model.Service svc) {
                if (!session.isOpen()) return;
                try {
                    sendJson(session, Map.of(
                            "type", action.name(),
                            "resource", "Service",
                            "object", Map.of(
                                    "name", svc.getMetadata().getName(),
                                    "namespace", svc.getMetadata().getNamespace()
                            )
                    ));
                } catch (Exception e) {
                    log.debug("Watch send error: {}", e.getMessage());
                }
            }

            @Override
            public void onClose(WatcherException e) {
                log.debug("Service watch closed for cluster={}", clusterId);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeWatches(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error: {}", exception.getMessage());
        closeWatches(session.getId());
    }

    private void closeWatches(String sessionId) {
        List<Watch> watches = sessionWatches.remove(sessionId);
        if (watches != null) {
            watches.forEach(w -> {
                try { w.close(); } catch (Exception ignored) {}
            });
        }
    }

    private void sendJson(WebSocketSession session, Map<String, ?> data) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(data)));
            }
        } catch (IOException e) {
            log.debug("Send WS message failed: {}", e.getMessage());
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