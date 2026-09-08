package com.hfwas.devops.user.security;

import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextServiceTest {

    @Mock
    private TenantAccessValidator tenantAccessValidator;
    @Mock
    private TenantMemberService tenantMemberService;

    private TenantContextService service;

    @BeforeEach
    void setUp() {
        service = new TenantContextService(tenantAccessValidator, tenantMemberService);
    }

    @Test
    void headerWinsOverJwtAndMembership() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "9");

        Long tenantId = service.resolveAndValidate(request, 7L, "user", 1L);

        assertEquals(9L, tenantId);
        verify(tenantAccessValidator).assertAccess(7L, "user", 9L);
        verify(tenantMemberService, never()).listEnabledTenantsByUser(anyLong());
    }

    @Test
    void fallsBackToDefaultMembershipWhenHeaderAndJwtMissing() {
        TenantOptionVO other = new TenantOptionVO();
        other.setId(3L);
        TenantOptionVO home = new TenantOptionVO();
        home.setId(TenantService.DEFAULT_TENANT_ID);
        when(tenantMemberService.listEnabledTenantsByUser(7L)).thenReturn(List.of(other, home));

        Long tenantId = service.resolveAndValidate(new MockHttpServletRequest(), 7L, "user", null);

        assertEquals(TenantService.DEFAULT_TENANT_ID, tenantId);
        verify(tenantAccessValidator).assertAccess(7L, "user", TenantService.DEFAULT_TENANT_ID);
    }

    @Test
    void adminWithoutMembershipUsesDefaultTenant() {
        when(tenantMemberService.listEnabledTenantsByUser(1L)).thenReturn(List.of());

        Long tenantId = service.resolveAndValidate(new MockHttpServletRequest(), 1L, "admin", null);

        assertEquals(TenantService.DEFAULT_TENANT_ID, tenantId);
        verify(tenantAccessValidator).assertAccess(1L, "admin", TenantService.DEFAULT_TENANT_ID);
    }

    @Test
    void userWithoutMembershipHasNoTenant() {
        when(tenantMemberService.listEnabledTenantsByUser(7L)).thenReturn(List.of());

        assertNull(service.resolveAndValidate(new MockHttpServletRequest(), 7L, "user", null));
        verify(tenantAccessValidator, never()).assertAccess(anyLong(), any(), anyLong());
    }
}
