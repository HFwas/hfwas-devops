package com.hfwas.devops.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-127、128：WebSocket 握手 JWT 提取与拒绝。
 */
class PodExecAuthHandshakeInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final PodExecAuthHandshakeInterceptor interceptor =
            new PodExecAuthHandshakeInterceptor(jwtDecoder);

    @Test
    @DisplayName("PIPE-127: 缺少子协议 JWT 返回 401")
    void missingTokenRejected() {
        ServerHttpRequest request = request();
        ServerHttpResponse response = response();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(request, response,
                mock(WebSocketHandler.class), attributes);

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("PIPE-128: 从子协议提取 JWT 并写入会话属性")
    void validTokenPopulatesAttributes() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("preferred_username", "ada")
                .claim("tenant_id", "9")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        ServerHttpRequest request = request();
        request.getHeaders().add("Sec-WebSocket-Protocol", "token");
        request.getHeaders().add("Sec-WebSocket-Protocol", "pipeline-exec");
        ServerHttpResponse response = response();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(request, response,
                mock(WebSocketHandler.class), attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes).containsEntry("userId", "user-1")
                .containsEntry("username", "ada")
                .containsEntry("tenantId", "9")
                .containsEntry("jwt", jwt);
        assertThat(response.getHeaders().getFirst("Sec-WebSocket-Protocol")).isEqualTo("token");
    }

    @Test
    @DisplayName("PIPE-128: 只有 pipeline-exec 子协议时回退取第一个")
    void fallbackToFirstProtocol() {
        when(jwtDecoder.decode("pipeline-exec")).thenThrow(new JwtException("bad token"));
        ServerHttpRequest request = request();
        request.getHeaders().add("Sec-WebSocket-Protocol", "pipeline-exec");
        ServerHttpResponse response = response();

        boolean allowed = interceptor.beforeHandshake(request, response,
                mock(WebSocketHandler.class), new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PIPE-127: 无效 JWT 返回 401")
    void invalidTokenRejected() {
        when(jwtDecoder.decode("bad")).thenThrow(new JwtException("bad token"));
        ServerHttpRequest request = request();
        request.getHeaders().add("Sec-WebSocket-Protocol", "bad");
        ServerHttpResponse response = response();

        boolean allowed = interceptor.beforeHandshake(request, response,
                mock(WebSocketHandler.class), new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private static ServerHttpRequest request() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        return request;
    }

    private static ServerHttpResponse response() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        return response;
    }
}
