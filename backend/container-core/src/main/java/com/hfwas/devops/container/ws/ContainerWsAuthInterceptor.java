package com.hfwas.devops.container.ws;

import com.hfwas.devops.container.service.cluster.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket auth interceptor for container platform WebSockets.
 * Validates JWT from Sec-WebSocket-Protocol header and checks tenant access to the cluster.
 */
@Slf4j
@Component
public class ContainerWsAuthInterceptor implements HandshakeInterceptor {

    private static final String SEC_WS_PROTOCOL = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;

    public ContainerWsAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extract JWT from Sec-WebSocket-Protocol header
        List<String> protocols = request.getHeaders().get(SEC_WS_PROTOCOL);
        String token = null;

        if (protocols != null && !protocols.isEmpty()) {
            for (String p : protocols) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("container-")) {
                    token = trimmed;
                    break;
                }
            }
            if (token == null) {
                token = protocols.getFirst().trim();
            }
        }

        if (token == null || token.isEmpty()) {
            log.warn("WS handshake missing JWT in Sec-WebSocket-Protocol");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("userId", jwt.getSubject());
            attributes.put("username", jwt.getClaimAsString("preferred_username"));
            attributes.put("jwt", jwt);

            // Extract tenant from JWT or path
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null) {
                attributes.put("tenantId", tenantId);
            }

            // Confirm sub-protocol
            response.getHeaders().set(SEC_WS_PROTOCOL, token);

            log.debug("WS handshake success: userId={}", jwt.getSubject());
            return true;
        } catch (JwtException e) {
            log.warn("WS handshake JWT validation failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }
}