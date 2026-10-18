package com.hfwas.devops.container.service.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.mapper.ClusterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterServiceSecurityTest {

    @Mock
    private ClusterMapper clusterMapper;
    @Mock
    private KubeconfigCipher kubeconfigCipher;
    @Mock
    private ClusterKubernetesClientFactory clientFactory;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ClusterService clusterService;

    @Test
    void nullTenantIsRejected() {
        assertThatThrownBy(() -> clusterService.getById(1L, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    @Test
    void foreignTenantIsForbidden() {
        ClusterEntity entity = new ClusterEntity();
        entity.setId(1L);
        entity.setTenantId(10L);
        when(clusterMapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> clusterService.getById(1L, 20L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ContainerErrorCode.CLUSTER_FORBIDDEN.getCode());
    }

    @Test
    void matchingTenantIsAllowed() {
        ClusterEntity entity = new ClusterEntity();
        entity.setId(1L);
        entity.setTenantId(10L);
        when(clusterMapper.selectById(1L)).thenReturn(entity);

        assertThat(clusterService.getById(1L, 10L)).isSameAs(entity);
    }
}
