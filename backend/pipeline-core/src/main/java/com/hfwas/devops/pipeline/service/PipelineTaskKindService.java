package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.config.TaskResourceLimitProperties;
import com.hfwas.devops.pipeline.dto.TaskKindParamSaveDTO;
import com.hfwas.devops.pipeline.dto.TaskKindParamVO;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PipelineTaskKindService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PipelineTaskKindMapper mapper;
    private final PipelineTaskKindParamMapper paramMapper;
    private final TaskResourceLimitProperties resourceLimits;

    public PipelineTaskKindService(
            PipelineTaskKindMapper mapper,
            PipelineTaskKindParamMapper paramMapper,
            TaskResourceLimitProperties resourceLimits
    ) {
        this.mapper = mapper;
        this.paramMapper = paramMapper;
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
        vo.setParams(paramMapper.selectByKindValue(entity.getKindValue()).stream()
                .map(this::toParamVo)
                .collect(Collectors.toList()));
        return vo;
    }

    private void replaceParams(String kind, List<TaskKindParamSaveDTO> params) {
        paramMapper.softDeleteByKindValue(kind);
        int order = 0;
        for (TaskKindParamSaveDTO dto : params) {
            if (dto.getParamKey() == null || dto.getParamKey().isBlank()) {
                continue;
            }
            PipelineTaskKindParamEntity entity = new PipelineTaskKindParamEntity();
            entity.setKindValue(kind);
            entity.setParamKey(dto.getParamKey().trim());
            entity.setParamLabel(dto.getParamLabel() != null && !dto.getParamLabel().isBlank()
                    ? dto.getParamLabel().trim() : dto.getParamKey().trim());
            entity.setParamType(dto.getParamType() != null ? dto.getParamType() : "input");
            entity.setDefaultValue(dto.getDefaultValue() != null ? dto.getDefaultValue() : "");
            entity.setRequired(dto.getRequired() != null && dto.getRequired() ? 1 : 0);
            entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
            entity.setOptionsJson(toJson(dto.getOptions()));
            entity.setApiUrl(dto.getApiUrl() != null ? dto.getApiUrl() : "");
            entity.setApiMethod(dto.getApiMethod() != null ? dto.getApiMethod() : "GET");
            entity.setApiHeadersJson(toJson(dto.getApiHeaders()));
            entity.setApiResponsePath(dto.getApiResponsePath() != null ? dto.getApiResponsePath() : "");
            entity.setPlaceholder(dto.getPlaceholder() != null ? dto.getPlaceholder() : "");
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
        return vo;
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