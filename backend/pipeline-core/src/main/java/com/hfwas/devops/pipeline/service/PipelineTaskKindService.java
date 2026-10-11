package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.config.TaskResourceLimitProperties;
import com.hfwas.devops.pipeline.dto.TaskKindParamSaveDTO;
import com.hfwas.devops.pipeline.dto.TaskKindParamVO;
import com.hfwas.devops.pipeline.dto.TaskKindPublishDTO;
import com.hfwas.devops.pipeline.dto.TaskKindSnapshot;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.dto.TaskKindVersionVO;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindVersionEntity;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindVersionMapper;
import com.hfwas.devops.pipeline.param.TaskKindParamVisibility;
import com.hfwas.devops.pipeline.param.TaskKindVersions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineTaskKindService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final java.util.regex.Pattern PARAM_KEY = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final PipelineTaskKindMapper mapper;
    private final PipelineTaskKindParamMapper paramMapper;
    private final PipelineTaskKindVersionMapper versionMapper;
    private final TaskResourceLimitProperties resourceLimits;

    public PipelineTaskKindService(
            PipelineTaskKindMapper mapper,
            PipelineTaskKindParamMapper paramMapper,
            PipelineTaskKindVersionMapper versionMapper,
            TaskResourceLimitProperties resourceLimits
    ) {
        this.mapper = mapper;
        this.paramMapper = paramMapper;
        this.versionMapper = versionMapper;
        this.resourceLimits = resourceLimits;
    }

    /** 返回所有记录（含禁用的），供管理页 */
    public List<TaskKindVO> listAll() {
        return mapper.selectList(null).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 仅返回已启用的，供编辑器/选择器使用 */
    public List<TaskKindVO> listEnabled() {
        return mapper.selectEnabled().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 获取单个 Task 定义 */
    public TaskKindVO getByKind(String kind) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        return toVO(entity);
    }

    @Transactional
    public void update(String kind, TaskKindUpdateDTO dto) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        entity.setLabel(dto.getLabel());
        if (dto.getTaskGroup() != null) {
            if (dto.getTaskGroup().isBlank()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "分组不能为空");
            }
            entity.setTaskGroup(dto.getTaskGroup().trim());
        }
        entity.setDescription(dto.getDescription());
        entity.setHint(dto.getHint());
        entity.setDefaultCommand(dto.getDefaultCommand());
        // toolImage 仅在前端显式传入时才更新（为空时不覆盖）
        if (dto.getToolImage() != null) {
            entity.setToolImage(dto.getToolImage());
        }
        entity.setCommandTemplate(dto.getCommandTemplate());
        entity.setSortOrder(dto.getSortOrder());
        // 资源字段校验与保存
        if (dto.getCpuRequest() != null) {
            validateResourceQuantity("cpuRequest", dto.getCpuRequest());
            validateCpuLimit("cpuRequest", dto.getCpuRequest());
            entity.setCpuRequest(dto.getCpuRequest());
        }
        if (dto.getCpuLimit() != null) {
            validateResourceQuantity("cpuLimit", dto.getCpuLimit());
            validateCpuLimit("cpuLimit", dto.getCpuLimit());
            entity.setCpuLimit(dto.getCpuLimit());
        }
        if (dto.getMemoryRequest() != null) {
            validateResourceQuantity("memoryRequest", dto.getMemoryRequest());
            validateMemoryLimit("memoryRequest", dto.getMemoryRequest());
            entity.setMemoryRequest(dto.getMemoryRequest());
        }
        if (dto.getMemoryLimit() != null) {
            validateResourceQuantity("memoryLimit", dto.getMemoryLimit());
            validateMemoryLimit("memoryLimit", dto.getMemoryLimit());
            entity.setMemoryLimit(dto.getMemoryLimit());
        }
        mapper.updateById(entity);
        if (dto.getParams() != null) {
            replaceParams(kind, dto.getParams());
        }
    }

    @Transactional
    public void toggle(String kind) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        entity.setEnabled(entity.getEnabled() == 1 ? 0 : 1);
        mapper.updateById(entity);
    }

    public List<TaskKindVersionVO> listVersions(String kind) {
        PipelineTaskKindEntity entity = requireEntity(kind);
        String current = entity.getVersion() == null ? "" : entity.getVersion();
        return versionMapper.selectByKind(kind).stream()
                .map(row -> toVersionVo(row, current))
                .collect(Collectors.toList());
    }

    /** 把当前工作副本打成快照并设为启用版本。不在保存时调用。 */
    @Transactional
    public TaskKindVersionVO publish(String kind, TaskKindPublishDTO dto) {
        PipelineTaskKindEntity entity = requireEntity(kind);
        String current = entity.getVersion() == null ? "" : entity.getVersion();
        String next = TaskKindVersions.resolve(current, dto == null ? null : dto.getVersion());
        if (versionMapper.selectByKindAndVersion(kind, next) != null) {
            throw BizException.of(ResultCode.DUPLICATE, "版本已存在: " + next);
        }
        String note = dto == null || dto.getNote() == null ? "" : dto.getNote().trim();
        if (note.length() > 500) {
            throw BizException.of(ResultCode.BAD_REQUEST, "版本备注不能超过 500 字");
        }
        PipelineTaskKindVersionEntity row = new PipelineTaskKindVersionEntity();
        row.setKindValue(kind);
        row.setVersion(next);
        row.setNote(note);
        row.setSnapshotJson(writeSnapshot(capture(entity)));
        row.setCreateTime(LocalDateTime.now());
        versionMapper.insert(row);
        entity.setVersion(next);
        mapper.updateById(entity);
        return toVersionVo(row, next);
    }

    /** 用历史快照覆盖工作副本，并把它标为当前启用版本。 */
    @Transactional
    public TaskKindVO activate(String kind, String version) {
        PipelineTaskKindEntity entity = requireEntity(kind);
        if (version == null || version.isBlank()) {
            throw BizException.of(ResultCode.BAD_REQUEST, "版本号不能为空");
        }
        PipelineTaskKindVersionEntity row = versionMapper.selectByKindAndVersion(kind, version);
        if (row == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "版本不存在: " + version);
        }
        TaskKindSnapshot snapshot = readSnapshot(row.getSnapshotJson());
        applySnapshot(entity, snapshot);
        entity.setVersion(row.getVersion());
        mapper.updateById(entity);
        replaceParams(kind, snapshot.getParams() == null ? List.of() : snapshot.getParams());
        return toVO(entity);
    }

    private PipelineTaskKindEntity requireEntity(String kind) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        return entity;
    }

    private TaskKindVO toVO(PipelineTaskKindEntity entity) {
        TaskKindVO vo = new TaskKindVO();
        vo.setKindValue(entity.getKindValue());
        vo.setLabel(entity.getLabel());
        vo.setTaskGroup(entity.getTaskGroup());
        vo.setDescription(entity.getDescription());
        vo.setHint(entity.getHint());
        vo.setDefaultCommand(entity.getDefaultCommand());
        vo.setRequiresCommand(entity.getRequiresCommand() == 1);
        vo.setEnabled(entity.getEnabled() == 1);
        vo.setSortOrder(entity.getSortOrder());
        vo.setToolImage(entity.getToolImage());
        vo.setDefaultImage(entity.getDefaultImage());
        vo.setCommandTemplate(entity.getCommandTemplate());
        vo.setCpuRequest(entity.getCpuRequest());
        vo.setCpuLimit(entity.getCpuLimit());
        vo.setMemoryRequest(entity.getMemoryRequest());
        vo.setMemoryLimit(entity.getMemoryLimit());
        vo.setVersion(entity.getVersion() == null ? "" : entity.getVersion());
        vo.setParams(paramMapper.selectByKindValue(entity.getKindValue()).stream()
                .map(this::toParamVo)
                .collect(Collectors.toList()));
        return vo;
    }

    private void replaceParams(String kind, List<TaskKindParamSaveDTO> params) {
        paramMapper.softDeleteByKindValue(kind);
        int order = 0;
        Set<String> seen = new HashSet<>();
        for (TaskKindParamSaveDTO dto : params) {
            if (dto.getParamKey() == null || dto.getParamKey().isBlank()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "参数键不能为空");
            }
            String key = dto.getParamKey().trim();
            if (!PARAM_KEY.matcher(key).matches()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "参数键只允许字母、数字和下划线: " + key);
            }
            if (!seen.add(key)) {
                throw BizException.of(ResultCode.DUPLICATE, "参数键重复: " + key);
            }
            String paramType = normalizeParamType(dto.getParamType());
            PipelineTaskKindParamEntity entity = new PipelineTaskKindParamEntity();
            entity.setKindValue(kind);
            entity.setParamKey(key);
            entity.setParamLabel(dto.getParamLabel() != null && !dto.getParamLabel().isBlank()
                    ? dto.getParamLabel().trim() : key);
            entity.setParamType(paramType);
            entity.setDefaultValue(dto.getDefaultValue() != null ? dto.getDefaultValue() : "");
            entity.setRequired(dto.getRequired() != null && dto.getRequired() ? 1 : 0);
            entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
            entity.setOptionsJson(toJson(dto.getOptions()));
            entity.setApiUrl(dto.getApiUrl() != null ? dto.getApiUrl() : "");
            entity.setApiMethod(dto.getApiMethod() != null ? dto.getApiMethod() : "GET");
            entity.setApiHeadersJson(toJson(dto.getApiHeaders()));
            entity.setApiResponsePath(dto.getApiResponsePath() != null ? dto.getApiResponsePath() : "");
            entity.setPlaceholder(dto.getPlaceholder() != null ? dto.getPlaceholder() : "");
            entity.setVisibility(TaskKindParamVisibility.require(dto.getVisibility()));
            entity.setDeleted(0);
            paramMapper.insert(entity);
            order++;
        }
    }

    private TaskKindParamVO toParamVo(PipelineTaskKindParamEntity entity) {
        TaskKindParamVO vo = new TaskKindParamVO();
        vo.setId(entity.getId());
        vo.setKindValue(entity.getKindValue());
        vo.setParamKey(entity.getParamKey());
        vo.setParamLabel(entity.getParamLabel());
        vo.setParamType(entity.getParamType());
        vo.setDefaultValue(entity.getDefaultValue());
        vo.setRequired(entity.getRequired() != null && entity.getRequired() == 1);
        vo.setSortOrder(entity.getSortOrder());
        vo.setOptions(parseJsonArray(entity.getOptionsJson()));
        vo.setApiUrl(entity.getApiUrl());
        vo.setApiMethod(entity.getApiMethod());
        vo.setApiHeaders(parseJsonMap(entity.getApiHeadersJson()));
        vo.setApiResponsePath(entity.getApiResponsePath());
        vo.setPlaceholder(entity.getPlaceholder());
        vo.setVisibility(TaskKindParamVisibility.normalizeOrUser(entity.getVisibility()));
        return vo;
    }

    private TaskKindSnapshot capture(PipelineTaskKindEntity entity) {
        TaskKindSnapshot snapshot = new TaskKindSnapshot();
        snapshot.setLabel(entity.getLabel());
        snapshot.setTaskGroup(entity.getTaskGroup());
        snapshot.setDescription(entity.getDescription());
        snapshot.setHint(entity.getHint());
        snapshot.setDefaultCommand(entity.getDefaultCommand());
        snapshot.setToolImage(entity.getToolImage());
        snapshot.setCommandTemplate(entity.getCommandTemplate());
        snapshot.setSortOrder(entity.getSortOrder());
        snapshot.setCpuRequest(entity.getCpuRequest());
        snapshot.setCpuLimit(entity.getCpuLimit());
        snapshot.setMemoryRequest(entity.getMemoryRequest());
        snapshot.setMemoryLimit(entity.getMemoryLimit());
        snapshot.setParams(paramMapper.selectByKindValue(entity.getKindValue()).stream()
                .map(this::toSaveDto)
                .collect(Collectors.toList()));
        return snapshot;
    }

    private void applySnapshot(PipelineTaskKindEntity entity, TaskKindSnapshot snapshot) {
        entity.setLabel(snapshot.getLabel());
        if (snapshot.getTaskGroup() != null && !snapshot.getTaskGroup().isBlank()) {
            entity.setTaskGroup(snapshot.getTaskGroup());
        }
        entity.setDescription(snapshot.getDescription() == null ? "" : snapshot.getDescription());
        entity.setHint(snapshot.getHint() == null ? "" : snapshot.getHint());
        entity.setDefaultCommand(snapshot.getDefaultCommand() == null ? "" : snapshot.getDefaultCommand());
        entity.setToolImage(snapshot.getToolImage() == null ? "" : snapshot.getToolImage());
        entity.setCommandTemplate(snapshot.getCommandTemplate() == null ? "" : snapshot.getCommandTemplate());
        entity.setSortOrder(snapshot.getSortOrder() == null ? 0 : snapshot.getSortOrder());
        entity.setCpuRequest(snapshot.getCpuRequest() == null ? "" : snapshot.getCpuRequest());
        entity.setCpuLimit(snapshot.getCpuLimit() == null ? "" : snapshot.getCpuLimit());
        entity.setMemoryRequest(snapshot.getMemoryRequest() == null ? "" : snapshot.getMemoryRequest());
        entity.setMemoryLimit(snapshot.getMemoryLimit() == null ? "" : snapshot.getMemoryLimit());
    }

    private TaskKindParamSaveDTO toSaveDto(PipelineTaskKindParamEntity entity) {
        TaskKindParamSaveDTO dto = new TaskKindParamSaveDTO();
        dto.setParamKey(entity.getParamKey());
        dto.setParamLabel(entity.getParamLabel());
        dto.setParamType(entity.getParamType());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setRequired(entity.getRequired() != null && entity.getRequired() == 1);
        dto.setSortOrder(entity.getSortOrder());
        dto.setOptions(parseJsonArray(entity.getOptionsJson()));
        dto.setApiUrl(entity.getApiUrl());
        dto.setApiMethod(entity.getApiMethod());
        dto.setApiHeaders(parseJsonMap(entity.getApiHeadersJson()));
        dto.setApiResponsePath(entity.getApiResponsePath());
        dto.setPlaceholder(entity.getPlaceholder());
        dto.setVisibility(TaskKindParamVisibility.normalizeOrUser(entity.getVisibility()));
        return dto;
    }

    private TaskKindVersionVO toVersionVo(PipelineTaskKindVersionEntity row, String current) {
        TaskKindVersionVO vo = new TaskKindVersionVO();
        vo.setVersion(row.getVersion());
        vo.setNote(row.getNote());
        vo.setCreatedAt(row.getCreateTime());
        vo.setIsCurrent(row.getVersion() != null && row.getVersion().equals(current));
        return vo;
    }

    private String writeSnapshot(TaskKindSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw BizException.of(ResultCode.OPERATION_FAILED, "版本快照写入失败");
        }
    }

    private TaskKindSnapshot readSnapshot(String json) {
        try {
            TaskKindSnapshot snapshot = JSON.readValue(json, TaskKindSnapshot.class);
            if (snapshot == null) {
                throw new IllegalStateException("empty snapshot");
            }
            return snapshot;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(ResultCode.OPERATION_FAILED, "版本快照无法读取");
        }
    }

    private static String normalizeParamType(String paramType) {
        String type = paramType == null || paramType.isBlank() ? "input" : paramType.trim();
        if (!"input".equals(type) && !"select".equals(type) && !"api_select".equals(type)) {
            throw BizException.of(ResultCode.BAD_REQUEST, "参数类型只支持 input、select、api_select");
        }
        return type;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return JSON.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String toJson(Object value) {
        if (value == null) return "";
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 校验 K8s 资源量格式是否合法（如 "500m"、"2Gi"、"1"）。
     * 利用 fabric8 Quantity 构造器进行解析，非法格式会抛出异常。
     */
    private void validateResourceQuantity(String field, String value) {
        if (value == null || value.isBlank()) return;
        try {
            new Quantity(value);
        } catch (Exception e) {
            throw BizException.of(ResultCode.BAD_REQUEST,
                    "Task 资源配置格式错误: " + field + " = \"" + value + "\"，需要合法的 K8s 资源量格式（如 500m、1、2Gi）");
        }
    }

    /**
     * 校验 CPU 值是否超过平台最大限制。
     */
    private void validateCpuLimit(String field, String value) {
        if (value == null || value.isBlank()) return;
        double maxCpu = resourceLimits.getMaxCpu();
        if (maxCpu <= 0) return; // 0 = 不限制
        try {
            double cpuValue = new Quantity(value).getNumericalAmount().doubleValue();
            if (cpuValue > maxCpu) {
                throw BizException.of(ResultCode.BAD_REQUEST,
                        "Task CPU 配置 \"" + field + "\" = " + cpuValue + " 超过平台最大限制 " + maxCpu + " 核");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Quantity 解析异常已在 validateResourceQuantity 中处理
        }
    }

    /**
     * 校验内存值是否超过平台最大限制。
     */
    private void validateMemoryLimit(String field, String value) {
        if (value == null || value.isBlank()) return;
        String maxMemory = resourceLimits.getMaxMemory();
        if (maxMemory == null || maxMemory.isBlank()) return;
        try {
            double memValue = new Quantity(value).getNumericalAmount().doubleValue();
            double maxMemValue = new Quantity(maxMemory).getNumericalAmount().doubleValue();
            if (memValue > maxMemValue) {
                throw BizException.of(ResultCode.BAD_REQUEST,
                        "Task 内存配置 \"" + field + "\" = " + value + " 超过平台最大限制 " + maxMemory);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Quantity 解析异常已在 validateResourceQuantity 中处理
        }
    }
}