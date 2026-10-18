package com.hfwas.devops.pm.field.service;

import com.hfwas.devops.pm.field.mapper.PmTypeFieldLayoutMapper;
import com.hfwas.devops.pm.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldLayoutServiceTest {

    @Mock
    private PmTypeFieldLayoutMapper layoutMapper;
    @Mock
    private ProjectService projectService;

    @InjectMocks
    private FieldLayoutService fieldLayoutService;

    @Test
    void noCrossProjectFallback() {
        when(layoutMapper.selectOne(any())).thenReturn(null);

        assertThat(fieldLayoutService.getLayout(1L, "TASK")).isNull();
        verify(layoutMapper, never()).selectList(any());
    }
}
