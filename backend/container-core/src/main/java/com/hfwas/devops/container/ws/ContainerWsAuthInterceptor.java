package com.hfwas.devops.container.ws;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.service.cluster.ClusterService;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import lombok.RequiredArgsConstructor;
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

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * WebSocket auth interceptor for container platform WebSockets.
 *
 * The JWT arrives in Sec-WebSocket-Protocol (browsers cannot set custom headers).
 * The tenant is a query parameter candidate, validated against membership; it is
 * never defaulted, so a missing tenant fails closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerWsAuthInterceptor implements HandshakeInterceptor {

    private static final String SEC_WS_PROTOCOL = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;
    private final KeycloakUserProvisioningService provisioningService;
    private final TenantAccessValidator tenantAccessValidator;
    private final ClusterService clusterService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            log.warn("WS handshake missing JWT in Sec-WebSocket-Protocol");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            SysUser user = provisioningService.ensureUser(
                    jwt.getSubject(),
                    jwt.getClaimAsString("preferred_username"),
                    jwt.getClaimAsString("email"),
                    firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("given_name")));
            if (user.getEnabled() != null && user.getEnabled() != 1) {
                log.warn("WS handshake rejected disabled user: {}", user.getId());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            Long tenantId = resolveTenant(request.getURI(), jwt);
            if (tenantId == null) {
                log.warn("WS handshake rejected: no tenant for user {}", user.getId());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            tenantAccessValidator.assertAccess(user.getId(), user.getRole(), tenantId);

            Long clusterId = resolveClusterId(request.getURI());
            if (clusterId == null) {
                log.warn("WS handshake rejected: missing cluster id in path {}", request.getURI().getPath());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            clusterService.getById(clusterId, tenantId);

            attributes.put("userId", user.getId());
            attributes.put("username", user.getUsername());
            attributes.put("tenantId", tenantId);
            attributes.put("jwt", jwt);

            response.getHeaders().set(SEC_WS_PROTOCOL, token);
            log.debug("WS handshake success: userId={}, tenantId={}, clusterId={}",
                    user.getId(), tenantId, clusterId);
            return true;
        } catch (JwtException e) {
            log.warn("WS handshake JWT validation failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (BizException e) {
            log.warn("WS handshake authorization failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        } catch (Exception e) {
            log.warn("WS handshake failed: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        List<String> protocols = request.getHeaders().get(SEC_WS_PROTOCOL);
        if (protocols == null) {
            return null;
        }
        for (String headerValue : protocols) {
            for (String part : headerValue.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("container-")) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private Long resolveTenant(URI uri, Jwt jwt) {
        String fromQuery = queryParam(uri, "tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) {
            try {
                return Long.parseLong(fromQuery.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String claim = jwt.getClaimAsString("tenant_id");
        if (claim != null && !claim.isBlank()) {
            try {
                return Long.parseLong(claim.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long resolveClusterId(URI uri) {
        String[] parts = uri.getPath().split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("logs".equals(parts[i]) || "watch".equals(parts[i]) || "shell".equals(parts[i])) {
                try {
                    return Long.parseLong(parts[i + 1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String queryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && name.equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }
}
