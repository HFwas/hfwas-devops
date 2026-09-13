package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.JobParamDefinitionVO;
import com.hfwas.devops.pipeline.dto.JobParamPreviewResultVO;
import com.hfwas.devops.pipeline.dto.JobParamSaveDTO;
import com.hfwas.devops.pipeline.dto.RunParamDefinitionVO;
import com.hfwas.devops.pipeline.entity.PipelineJobParamEntity;
import com.hfwas.devops.pipeline.mapper.PipelineJobParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PipelineJobParamService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final PipelineJobParamMapper mapper;

    public PipelineJobParamService(PipelineJobParamMapper mapper) {
        this.mapper = mapper;
    }

    // ---- CRUD ----

    public List<JobParamDefinitionVO> listByPipeline(Long pipelineId) {
        return mapper.selectByPipelineId(pipelineId).stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public List<JobParamDefinitionVO> listByJob(Long jobId) {
        return mapper.selectByJobId(jobId).stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public JobParamDefinitionVO getById(Long id) {
        PipelineJobParamEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "参数定义不存在");
        }
        return toVo(entity);
    }

    @Transactional
    public Long create(JobParamSaveDTO dto) {
        PipelineJobParamEntity entity = toEntity(dto);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(Long id, JobParamSaveDTO dto) {
        PipelineJobParamEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "参数定义不存在");
        }
        applyDto(entity, dto);
        mapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        PipelineJobParamEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "参数定义不存在");
        }
        mapper.deleteById(id);
    }

    // ---- 获取运行时默认参数 ----

    public List<RunParamDefinitionVO> getDefaultParams(Long pipelineId) {
        List<PipelineJobParamEntity> entities = mapper.selectByPipelineId(pipelineId);
        return entities.stream()
                .filter(PipelineJobParamService::isRuntimeValue)
                .map(this::toRunParamVo)
                .collect(Collectors.toList());
    }

    // ---- 预览远程 API ----

    public JobParamPreviewResultVO previewApi(String apiUrl, String apiMethod,
                                              Map<String, String> apiHeaders,
                                              String apiResponsePath) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15));

            if ("POST".equalsIgnoreCase(apiMethod)) {
                builder = builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder = builder.GET();
            }

            if (apiHeaders != null) {
                for (Map.Entry<String, String> entry : apiHeaders.entrySet()) {
                    builder = builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = HTTP.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                JobParamPreviewResultVO result = new JobParamPreviewResultVO();
                result.setSuccess(false);
                result.setErrorMessage("API 返回状态码: " + response.statusCode());
                return result;
            }

            String body = response.body();
            List<String> options = extractOptions(body, apiResponsePath);
            JobParamPreviewResultVO result = new JobParamPreviewResultVO();
            result.setSuccess(true);
            result.setOptions(options);
            return result;
        } catch (Exception e) {
            JobParamPreviewResultVO result = new JobParamPreviewResultVO();
            result.setSuccess(false);
            result.setErrorMessage("API 调用失败: " + e.getMessage());
            return result;
        }
    }

    // ---- 获取流水线中所有 job 的参数定义映射 ----

    public Map<Long, List<PipelineJobParamEntity>> getParamsByPipelineGroupedByJob(Long pipelineId) {
        List<PipelineJobParamEntity> all = mapper.selectByPipelineId(pipelineId);
        return all.stream()
                .collect(Collectors.groupingBy(PipelineJobParamEntity::getJobId));
    }

    // ---- 内部方法 ----

    @SuppressWarnings("unchecked")
    private List<String> extractOptions(String body, String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            // 默认取 JSON 顶层数组
            try {
                return JSON.readValue(body, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return List.of();
            }
        }
        // 简易 JSONPath 支持: $[].key 或 $.key[].subkey
        String path = jsonPath.trim();
        try {
            Object root = JSON.readValue(body, Object.class);
            Object current = root;
            // 移除开头的 $
            if (path.startsWith("$")) {
                path = path.substring(1);
            }
            // 去除前导点号
            if (path.startsWith(".")) {
                path = path.substring(1);
            }
            String[] segments = path.split("\\.");
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                if (segment.contains("[]")) {
                    String key = segment.replace("[]", "");
                    if (current instanceof Map) {
                        current = ((Map<String, Object>) current).get(key);
                    }
                    if (current instanceof List) {
                        List<Object> list = (List<Object>) current;
                        List<String> result = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof String) {
                                result.add((String) item);
                            } else if (item instanceof Map) {
                                // 取剩余路径
                                String remaining = getRemainingPath(segments, segment);
                                if (!remaining.isEmpty()) {
                                    Object nested = resolveNested((Map<String, Object>) item, remaining);
                                    if (nested instanceof String) {
                                        result.add((String) nested);
                                    }
                                }
                            }
                        }
                        return result;
                    }
                } else {
                    if (current instanceof Map) {
                        current = ((Map<String, Object>) current).get(segment);
                    }
                }
            }
            if (current instanceof List) {
                List<Object> list = (List<Object>) current;
                return list.stream()
                        .filter(item -> item instanceof String)
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String getRemainingPath(String[] segments, String currentSegment) {
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String s : segments) {
            if (s.equals(currentSegment)) {
                found = true;
                continue;
            }
            if (found) {
                if (sb.length() > 0) sb.append(".");
                sb.append(s);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolveNested(Map<String, Object> map, String path) {
        Object current = map;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    // ---- VO / Entity 转换 ----

    private JobParamDefinitionVO toVo(PipelineJobParamEntity entity) {
        JobParamDefinitionVO vo = new JobParamDefinitionVO();
        vo.setId(entity.getId());
        vo.setPipelineId(entity.getPipelineId());
        vo.setJobId(entity.getJobId());
        vo.setParamKey(entity.getParamKey());
        vo.setParamLabel(entity.getParamLabel());
        vo.setParamType(entity.getParamType());
        vo.setValueMode(normalizeValueMode(entity.getValueMode()));
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

    private RunParamDefinitionVO toRunParamVo(PipelineJobParamEntity entity) {
        RunParamDefinitionVO vo = new RunParamDefinitionVO();
        vo.setParamKey(entity.getParamKey());
        vo.setParamLabel(entity.getParamLabel());
        vo.setParamType(entity.getParamType());
        vo.setDefaultValue(entity.getDefaultValue());
        vo.setRequired(entity.getRequired() != null && entity.getRequired() == 1);
        vo.setPlaceholder(entity.getPlaceholder());
        // 对 api_select 类型，options 由前端异步拉取
        if ("select".equals(entity.getParamType())) {
            vo.setOptions(parseJsonArray(entity.getOptionsJson()));
        } else if ("api_select".equals(entity.getParamType())) {
            vo.setLoading(true);
        }
        return vo;
    }

    private PipelineJobParamEntity toEntity(JobParamSaveDTO dto) {
        PipelineJobParamEntity entity = new PipelineJobParamEntity();
        applyDto(entity, dto);
        return entity;
    }

    private void applyDto(PipelineJobParamEntity entity, JobParamSaveDTO dto) {
        entity.setPipelineId(dto.getPipelineId());
        entity.setJobId(dto.getJobId());
        entity.setParamKey(dto.getParamKey());
        entity.setParamLabel(dto.getParamLabel());
        entity.setParamType(dto.getParamType() != null ? dto.getParamType() : "input");
        entity.setValueMode(normalizeValueMode(dto.getValueMode()));
        entity.setDefaultValue(dto.getDefaultValue() != null ? dto.getDefaultValue() : "");
        entity.setRequired(dto.getRequired() != null && dto.getRequired() ? 1 : 0);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setOptionsJson(toJson(dto.getOptions()));
        entity.setApiUrl(dto.getApiUrl() != null ? dto.getApiUrl() : "");
        entity.setApiMethod(dto.getApiMethod() != null ? dto.getApiMethod() : "GET");
        entity.setApiHeadersJson(toJson(dto.getApiHeaders()));
        entity.setApiResponsePath(dto.getApiResponsePath() != null ? dto.getApiResponsePath() : "");
        entity.setPlaceholder(dto.getPlaceholder() != null ? dto.getPlaceholder() : "");
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return JSON.readValue(json, new TypeReference<Map<String, String>>() {});
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

    static String normalizeValueMode(String valueMode) {
        if (valueMode != null && "fixed".equalsIgnoreCase(valueMode.trim())) {
            return "fixed";
        }
        return "runtime";
    }

    static boolean isRuntimeValue(PipelineJobParamEntity entity) {
        return !"fixed".equals(normalizeValueMode(entity.getValueMode()));
    }
}