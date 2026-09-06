package com.hfwas.devops.user.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserProvisioningServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private TenantMemberService tenantMemberService;

    private KeycloakUserProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserProvisioningService(userMapper, tenantMemberService);
    }

    @Test
    void firstUserBecomesAdminAndJoinsDefaultTenant() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        SysUser created = service.ensureUser("sub-admin", "admin", "admin@localhost", "系统管理员");

        assertEquals("admin", created.getRole());
        assertEquals("keycloak", created.getAuthSource());
        assertEquals("sub-admin", created.getExternalId());
        assertNull(created.getPassword());
        verify(tenantMemberService).ensureMember(TenantService.DEFAULT_TENANT_ID, 1L, "tenant_admin");
    }

    @Test
    void laterUserKeepsUserRoleAndGetsSuffixWhenUsernameTaken() {
        SysUser taken = new SysUser();
        taken.setId(3L);
        taken.setUsername("jane");
        taken.setExternalId("other-sub");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null, taken);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(8L);
            return 1;
        });

        SysUser created = service.ensureUser("abcdef12-xxxx", "jane", null, "Jane");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("user", created.getRole());
        assertEquals("jane-abcdef12", captor.getValue().getUsername());
        verify(tenantMemberService).ensureMember(eq(TenantService.DEFAULT_TENANT_ID), eq(8L), eq("member"));
    }
}
