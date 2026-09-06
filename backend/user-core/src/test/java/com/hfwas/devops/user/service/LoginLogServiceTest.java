package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hfwas.devops.user.entity.SysLoginLog;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysLoginLogMapper;
import com.hfwas.devops.user.model.KeycloakAuthEvent;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private KeycloakUserProvisioningService provisioningService;

    private LoginLogService service;

    @BeforeEach
    void setUp() {
        service = new LoginLogService(loginLogMapper, provisioningService);
    }

    @Test
    void ingestIgnoresRefreshToken() {
        KeycloakAuthEvent event = new KeycloakAuthEvent();
        event.setId("evt-1");
        event.setType("REFRESH_TOKEN");
        service.ingest(event);
        verify(loginLogMapper, never()).insert(any(SysLoginLog.class));
    }

    @Test
    void ingestIsIdempotentOnSameEventId() {
        when(loginLogMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        KeycloakAuthEvent event = loginEvent();
        service.ingest(event);
        verify(loginLogMapper, never()).insert(any(SysLoginLog.class));
        verify(provisioningService, never()).ensureUser(any(), any(), any(), any());
    }

    @Test
    void ingestLoginProvisionsAndWritesSuccess() {
        when(loginLogMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("admin");
        user.setDisplayName("系统管理员");
        when(provisioningService.ensureUser("sub-1", "admin", null, "admin")).thenReturn(user);

        service.ingest(loginEvent());

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        SysLoginLog log = captor.getValue();
        assertEquals("evt-login", log.getKcEventId());
        assertEquals(LoginLogService.ACTION_LOGIN_SUCCESS, log.getAction());
        assertEquals(9L, log.getUserId());
        assertEquals("admin", log.getUsername());
        assertNull(log.getFailReason());
    }

    @Test
    void ingestLoginErrorDoesNotProvision() {
        when(loginLogMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        KeycloakAuthEvent event = new KeycloakAuthEvent();
        event.setId("evt-fail");
        event.setType("LOGIN_ERROR");
        event.setError("invalid_user_credentials");
        event.setDetails(Map.of("username", "ghost"));

        service.ingest(event);

        verify(provisioningService, never()).ensureUser(any(), any(), any(), any());
        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        assertEquals(LoginLogService.ACTION_LOGIN_FAIL, captor.getValue().getAction());
        assertEquals("invalid_user_credentials", captor.getValue().getFailReason());
        assertEquals("ghost", captor.getValue().getUsername());
    }

    private static KeycloakAuthEvent loginEvent() {
        KeycloakAuthEvent event = new KeycloakAuthEvent();
        event.setId("evt-login");
        event.setType("LOGIN");
        event.setUserId("sub-1");
        event.setIpAddress("127.0.0.1");
        event.setTime(1_700_000_000_000L);
        event.setDetails(Map.of("username", "admin"));
        return event;
    }
}
