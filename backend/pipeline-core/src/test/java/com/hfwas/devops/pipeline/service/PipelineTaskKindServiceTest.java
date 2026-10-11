package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.config.TaskResourceLimitProperties;
import com.hfwas.devops.pipeline.dto.TaskKindPublishDTO;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindVersionEntity;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineTaskKindServiceTest {

    @Mock
    private PipelineTaskKindMapper mapper;
    @Mock
    private PipelineTaskKindParamMapper paramMapper;
    @Mock
    private PipelineTaskKindVersionMapper versionMapper;
    @Mock
    private TaskResourceLimitProperties resourceLimits;

    private PipelineTaskKindService service;

    @BeforeEach
    void setUp() {
        service = new PipelineTaskKindService(mapper, paramMapper, versionMapper, resourceLimits);
    }

    @Test
    void saveDoesNotChangeVersion() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.2.0");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setDescription("新描述");
        dto.setTaskGroup("构建");

        service.update("BUILD", dto);

        assertEquals("1.2.0", entity.getVersion());
        verify(versionMapper, never()).insert(any(PipelineTaskKindVersionEntity.class));
    }

    @Test
    void publishAssignsFirstVersionThenPatch() {
        PipelineTaskKindEntity entity = kind("BUILD", "");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        when(paramMapper.selectByKindValue("BUILD")).thenReturn(List.of());
        when(versionMapper.selectByKindAndVersion("BUILD", "1.0.0")).thenReturn(null);

        var published = service.publish("BUILD", new TaskKindPublishDTO());

        assertEquals("1.0.0", published.getVersion());
        assertEquals(true, published.getIsCurrent());
        assertEquals("1.0.0", entity.getVersion());
        ArgumentCaptor<PipelineTaskKindVersionEntity> captor = ArgumentCaptor.forClass(PipelineTaskKindVersionEntity.class);
        verify(versionMapper).insert(captor.capture());
        assertTrue(captor.getValue().getSnapshotJson().contains("\"label\":\"构建\""));

        when(versionMapper.selectByKindAndVersion("BUILD", "1.0.1")).thenReturn(null);
        var next = service.publish("BUILD", new TaskKindPublishDTO());
        assertEquals("1.0.1", next.getVersion());
        assertEquals("1.0.1", entity.getVersion());
    }

    @Test
    void publishRejectsDuplicateVersion() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.0.0");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        when(versionMapper.selectByKindAndVersion("BUILD", "1.0.0")).thenReturn(new PipelineTaskKindVersionEntity());
        TaskKindPublishDTO dto = new TaskKindPublishDTO();
        dto.setVersion("1.0.0");

        assertThrows(BizException.class, () -> service.publish("BUILD", dto));
        verify(versionMapper, never()).insert(any(PipelineTaskKindVersionEntity.class));
    }

    @Test
    void activateRestoresSnapshotAndParams() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.0.1");
        entity.setLabel("草稿名");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        PipelineTaskKindVersionEntity row = new PipelineTaskKindVersionEntity();
        row.setVersion("1.0.0");
        row.setSnapshotJson("""
                {"label":"构建","taskGroup":"构建","description":"旧描述","hint":"","defaultCommand":"","toolImage":"maven:3","commandTemplate":"mvn package","sortOrder":10,"cpuRequest":"500m","cpuLimit":"1","memoryRequest":"512Mi","memoryLimit":"1Gi","params":[{"paramKey":"PROFILE","paramLabel":"Profile","paramType":"input","defaultValue":"dev","required":false,"visibility":"hidden"}]}
                """);
        when(versionMapper.selectByKindAndVersion("BUILD", "1.0.0")).thenReturn(row);
        when(paramMapper.selectByKindValue("BUILD")).thenReturn(List.of());

        TaskKindVO vo = service.activate("BUILD", "1.0.0");

        assertEquals("1.0.0", entity.getVersion());
        assertEquals("构建", entity.getLabel());
        assertEquals("mvn package", entity.getCommandTemplate());
        assertEquals("1.0.0", vo.getVersion());
        ArgumentCaptor<PipelineTaskKindParamEntity> paramCaptor = ArgumentCaptor.forClass(PipelineTaskKindParamEntity.class);
        verify(paramMapper).softDeleteByKindValue("BUILD");
        verify(paramMapper).insert(paramCaptor.capture());
        assertEquals("PROFILE", paramCaptor.getValue().getParamKey());
        assertEquals("hidden", paramCaptor.getValue().getVisibility());
    }

    private static PipelineTaskKindEntity kind(String kind, String version) {
        PipelineTaskKindEntity entity = new PipelineTaskKindEntity();
        entity.setKindValue(kind);
        entity.setLabel("构建");
        entity.setTaskGroup("构建");
        entity.setDescription("");
        entity.setHint("");
        entity.setDefaultCommand("");
        entity.setRequiresCommand(1);
        entity.setEnabled(1);
        entity.setSortOrder(1);
        entity.setVersion(version);
        return entity;
    }
}
