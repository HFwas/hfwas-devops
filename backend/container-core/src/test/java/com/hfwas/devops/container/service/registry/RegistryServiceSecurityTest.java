package com.hfwas.devops.container.service.registry;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.container.entity.RegistryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.mapper.RegistryMapper;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryServiceSecurityTest {

    @Mock
    private RegistryMapper registryMapper;
    @Mock
    private RegistryCredentialCipher credentialCipher;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nullTenantIsRejected() {
        RegistryService service = new RegistryService(registryMapper, credentialCipher);

        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    @Test
    void foreignTenantRegistryIsForbidden() {
        authenticate(10L);
        RegistryEntity entity = new RegistryEntity();
        entity.setId(1L);
        entity.setTenantId(20L);
        when(registryMapper.selectById(1L)).thenReturn(entity);
        RegistryService service = new RegistryService(registryMapper, credentialCipher);

        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ContainerErrorCode.REGISTRY_FORBIDDEN.getCode());
    }

    private static void authenticate(long tenantId) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("tester");
        user.setRole("user");
        user.setEnabled(1);
        AuthUserPrincipal principal = new AuthUserPrincipal(user, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
