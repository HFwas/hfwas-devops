package com.hfwas.devops.container.ws;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.cluster.ClusterService;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContainerWsAuthInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private KeycloakUserProvisioningService provisioningService;
    @Mock
    private TenantAccessValidator tenantAccessValidator;
    @Mock
    private ClusterService clusterService;

    @Test
    void validTenantAndClusterIsAccepted() {
        ContainerWsAuthInterceptor interceptor = interceptor();
        SysUser user = user(1);
        Jwt jwt = jwt("sub-1");
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(provisioningService.ensureUser("sub-1", "u", "u@example.com", "User")).thenReturn(user);
        when(clusterService.getById(5L, 10L)).thenReturn(new ClusterEntity());

        Map<String, Object> attributes = new HashMap<>();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        boolean accepted = interceptor.beforeHandshake(
                request("ws://localhost/ws/container/logs/5?tenantId=10"),
                response,
                mock(WebSocketHandler.class),
                attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(7L);
        assertThat(attributes.get("tenantId")).isEqualTo(10L);
    }

    @Test
    void missingTenantIsRejected() {
        ContainerWsAuthInterceptor interceptor = interceptor();
        Jwt jwt = jwtWithoutTenant("sub-1");
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(provisioningService.ensureUser("sub-1", "u", "u@example.com", "User")).thenReturn(user(1));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean accepted = interceptor.beforeHandshake(
                request("ws://localhost/ws/container/logs/5"),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void foreignClusterIsRejected() {
        ContainerWsAuthInterceptor interceptor = interceptor();
        when(jwtDecoder.decode("token")).thenReturn(jwt("sub-1"));
        when(provisioningService.ensureUser("sub-1", "u", "u@example.com", "User")).thenReturn(user(1));
        when(clusterService.getById(5L, 10L)).thenThrow(new BizException(ContainerErrorCode.CLUSTER_FORBIDDEN));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean accepted = interceptor.beforeHandshake(
                request("ws://localhost/ws/container/logs/5?tenantId=10"),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void disabledUserIsRejected() {
        ContainerWsAuthInterceptor interceptor = interceptor();
        when(jwtDecoder.decode("token")).thenReturn(jwt("sub-1"));
        when(provisioningService.ensureUser("sub-1", "u", "u@example.com", "User")).thenReturn(user(0));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean accepted = interceptor.beforeHandshake(
                request("ws://localhost/ws/container/logs/5?tenantId=10"),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    private ContainerWsAuthInterceptor interceptor() {
        return new ContainerWsAuthInterceptor(
                jwtDecoder, provisioningService, tenantAccessValidator, clusterService);
    }

    private SysUser user(int enabled) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("u");
        user.setRole("user");
        user.setEnabled(enabled);
        return user;
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", "u")
                .claim("email", "u@example.com")
                .claim("name", "User")
                .claim("tenant_id", "10")
                .build();
    }

    private Jwt jwtWithoutTenant(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", "u")
                .claim("email", "u@example.com")
                .claim("name", "User")
                .build();
    }

    private ServerHttpRequest request(String uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "token");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        lenient().when(request.getHeaders()).thenReturn(headers);
        lenient().when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
