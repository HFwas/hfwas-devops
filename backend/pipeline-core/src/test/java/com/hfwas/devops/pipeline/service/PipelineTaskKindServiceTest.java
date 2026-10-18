package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.config.TaskResourceLimitProperties;
import com.hfwas.devops.pipeline.dto.TaskKindParamSaveDTO;
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

    @Test
    void updateRejectsMissingKind() {
        when(mapper.selectByKind("NOPE")).thenReturn(null);
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("x");

        BizException ex = assertThrows(BizException.class, () -> service.update("NOPE", dto));

        assertEquals("Task 类型不存在: NOPE", ex.getMessage());
    }

    @Test
    void updateRejectsBlankTaskGroup() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setTaskGroup("  ");

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertEquals("分组不能为空", ex.getMessage());
        verify(mapper, never()).updateById(any(PipelineTaskKindEntity.class));
    }

    @Test
    void updateKeepsToolImageWhenDtoFieldIsNull() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.0.0");
        entity.setToolImage("maven:3.9");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setToolImage(null);

        service.update("BUILD", dto);

        assertEquals("maven:3.9", entity.getToolImage());
    }

    @Test
    void updateRejectsCpuOverPlatformLimit() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.0.0");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);
        when(resourceLimits.getMaxCpu()).thenReturn(8.0);
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setCpuLimit("16");

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertTrue(ex.getMessage().contains("超过平台最大限制"));
    }

    @Test
    void updateRejectsMemoryOverPlatformLimit() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        when(resourceLimits.getMaxMemory()).thenReturn("32Gi");
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setMemoryLimit("64Gi");

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertTrue(ex.getMessage().contains("超过平台最大限制"));
    }

    @Test
    void updateReplacesParamsAndRejectsDuplicateKey() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setParams(List.of(param("PROFILE", "dev"), param("PROFILE", "prod")));

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertEquals("参数键重复: PROFILE", ex.getMessage());
        verify(paramMapper).softDeleteByKindValue("BUILD");
    }

    @Test
    void updateRejectsInvalidParamKey() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        dto.setParams(List.of(param("1-bad", "x")));

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertTrue(ex.getMessage().contains("参数键只允许字母、数字和下划线"));
    }

    @Test
    void updateRejectsUnsupportedParamType() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        TaskKindParamSaveDTO param = param("PROFILE", "dev");
        param.setParamType("radio");
        dto.setParams(List.of(param));

        BizException ex = assertThrows(BizException.class, () -> service.update("BUILD", dto));

        assertTrue(ex.getMessage().contains("参数类型只支持"));
    }

    @Test
    void updateAppliesParamDefaultsAndVisibility() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindUpdateDTO dto = new TaskKindUpdateDTO();
        dto.setLabel("构建");
        TaskKindParamSaveDTO param = param("PROFILE", "dev");
        param.setParamLabel(null);
        param.setParamType(null);
        param.setVisibility("hidden");
        dto.setParams(List.of(param));

        service.update("BUILD", dto);

        ArgumentCaptor<PipelineTaskKindParamEntity> captor = ArgumentCaptor.forClass(PipelineTaskKindParamEntity.class);
        verify(paramMapper).insert(captor.capture());
        assertEquals("PROFILE", captor.getValue().getParamLabel());
        assertEquals("input", captor.getValue().getParamType());
        assertEquals("GET", captor.getValue().getApiMethod());
        assertEquals("hidden", captor.getValue().getVisibility());
    }

    @Test
    void toggleFlipsEnabledAndRejectsMissing() {
        PipelineTaskKindEntity entity = kind("BUILD", "1.0.0");
        when(mapper.selectByKind("BUILD")).thenReturn(entity);

        service.toggle("BUILD");
        assertEquals(0, entity.getEnabled());

        when(mapper.selectByKind("NOPE")).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.toggle("NOPE"));
        assertEquals("Task 类型不存在: NOPE", ex.getMessage());
    }

    @Test
    void listVersionsMarksCurrent() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.1"));
        PipelineTaskKindVersionEntity current = new PipelineTaskKindVersionEntity();
        current.setVersion("1.0.1");
        PipelineTaskKindVersionEntity older = new PipelineTaskKindVersionEntity();
        older.setVersion("1.0.0");
        when(versionMapper.selectByKind("BUILD")).thenReturn(List.of(current, older));

        var versions = service.listVersions("BUILD");

        assertEquals(true, versions.getFirst().getIsCurrent());
        assertEquals(false, versions.get(1).getIsCurrent());
    }

    @Test
    void activateRejectsBlankAndMissingVersion() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));

        BizException blank = assertThrows(BizException.class, () -> service.activate("BUILD", " "));
        assertEquals("版本号不能为空", blank.getMessage());

        when(versionMapper.selectByKindAndVersion("BUILD", "9.9.9")).thenReturn(null);
        BizException missing = assertThrows(BizException.class, () -> service.activate("BUILD", "9.9.9"));
        assertEquals("版本不存在: 9.9.9", missing.getMessage());
    }

    @Test
    void publishRejectsOverlongNote() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        TaskKindPublishDTO dto = new TaskKindPublishDTO();
        dto.setNote("x".repeat(501));

        BizException ex = assertThrows(BizException.class, () -> service.publish("BUILD", dto));

        assertEquals("版本备注不能超过 500 字", ex.getMessage());
    }

    @Test
    void getReturnsParamsAndEnabledFlag() {
        when(mapper.selectByKind("BUILD")).thenReturn(kind("BUILD", "1.0.0"));
        when(paramMapper.selectByKindValue("BUILD")).thenReturn(List.of());

        TaskKindVO vo = service.getByKind("BUILD");

        assertEquals("BUILD", vo.getKindValue());
        assertEquals(true, vo.getEnabled());
        assertTrue(vo.getParams().isEmpty());
    }

    @Test
    void listAllIncludesDisabledKinds() {
        PipelineTaskKindEntity disabled = kind("OLD", "1.0.0");
        disabled.setEnabled(0);
        when(mapper.selectList(null)).thenReturn(List.of(disabled));

        List<TaskKindVO> rows = service.listAll();

        assertEquals(1, rows.size());
        assertEquals(false, rows.getFirst().getEnabled());
    }

    private static TaskKindParamSaveDTO param(String key, String defaultValue) {
        TaskKindParamSaveDTO dto = new TaskKindParamSaveDTO();
        dto.setParamKey(key);
        dto.setParamLabel(key);
        dto.setParamType("input");
        dto.setDefaultValue(defaultValue);
        return dto;
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
