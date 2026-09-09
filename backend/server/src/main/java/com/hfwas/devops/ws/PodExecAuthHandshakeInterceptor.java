package com.hfwas.devops.ws;

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

@Slf4j
@Component
public class PodExecAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final String SUBPROTOCOL_PREFIX = "pipeline-exec";
    private static final String SEC_WS_PROTOCOL = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;

    public PodExecAuthHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 Sec-WebSocket-Protocol 头部提取 JWT
        List<String> protocols = request.getHeaders().get(SEC_WS_PROTOCOL);
        String token = null;

        if (protocols != null && !protocols.isEmpty()) {
            // 客户端发送 new WebSocket(url, [jwt, 'pipeline-exec'])
            // protocols = [<jwt>, pipeline-exec]
            // 取第一个非 pipeline-exec 前缀的作为 token
            for (String p : protocols) {
                String trimmed = p.trim();
                if (!trimmed.startsWith(SUBPROTOCOL_PREFIX) && !trimmed.isEmpty()) {
                    token = trimmed;
                    break;
                }
            }
            // 如果没找到，回退取第一个
            if (token == null) {
                token = protocols.getFirst().trim();
            }
        }

        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手缺少 JWT（Sec-WebSocket-Protocol 为空）");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");
            String tenantId = jwt.getClaimAsString("tenant_id");

            if (tenantId != null) {
                attributes.put("tenantId", tenantId);
            }
            attributes.put("userId", userId);
            attributes.put("username", username != null ? username : userId);
            attributes.put("jwt", jwt);

            // 确认子协议：返回客户端提供的协议
            response.getHeaders().set(SEC_WS_PROTOCOL, token);

            log.debug("WebSocket 握手成功: userId={}, username={}", userId, username);
            return true;
        } catch (JwtException e) {
            log.warn("WebSocket 握手 JWT 验证失败: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}