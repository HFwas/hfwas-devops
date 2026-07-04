package com.hfwas.devops.pm.workitem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.workitem.entity.PmStatusDefinition;
import com.hfwas.devops.pm.workitem.mapper.PmStatusDefinitionMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.model.StatusWorkflowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatusDefinitionService {

    public static final String ANY_STATUS_CODE = "__any__";

    private final PmStatusDefinitionMapper statusDefinitionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StatusWorkflowVO getWorkflow(Long projectId, String typeCode) {
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        List<PmStatusDefinition> projectRows = listByScope(projectId, typeCode);
        boolean customized = !projectRows.isEmpty();
        List<PmStatusDefinition> rows = customized ? projectRows : listByScope(null, typeCode);
        if (rows.isEmpty()) {
            rows = defaultStatuses(typeCode);
        }
        StatusWorkflowVO vo = new StatusWorkflowVO();
        vo.setProjectId(projectId);
        vo.setTypeCode(typeCode);
        vo.setCustomized(customized);
        vo.setStatuses(toVoList(rows));
        return vo;
    }

    public List<StatusDefinitionVO> listStatusOptions(Long projectId, String typeCode) {
        return getWorkflow(projectId, typeCode).getStatuses().stream()
                .filter(s -> !ANY_STATUS_CODE.equals(s.getStatusCode()))
                .toList();
    }

    public AllowedTransitionsVO allowedTransitions(Long projectId, String typeCode, String fromStatus) {
        StatusWorkflowVO workflow = getWorkflow(projectId, typeCode);
        List<StatusDefinitionVO> all = workflow.getStatuses().stream()
                .filter(s -> !ANY_STATUS_CODE.equals(s.getStatusCode()))
                .toList();
        Map<String, StatusDefinitionVO> byCode = all.stream()
                .collect(Collectors.toMap(StatusDefinitionVO::getStatusCode, s -> s, (a, b) -> a, LinkedHashMap::new));
        Set<String> allowedCodes = new HashSet<>();
        if (StringUtils.isNotBlank(fromStatus)) {
            allowedCodes.add(fromStatus);
            StatusDefinitionVO from = findStatus(workflow.getStatuses(), fromStatus);
            if (from != null && from.getTransitions() != null) {
                allowedCodes.addAll(from.getTransitions());
            }
            StatusDefinitionVO any = findStatus(workflow.getStatuses(), ANY_STATUS_CODE);
            if (any != null && any.getTransitions() != null) {
                allowedCodes.addAll(any.getTransitions());
            }
        } else {
            allowedCodes.addAll(byCode.keySet());
        }
        AllowedTransitionsVO vo = new AllowedTransitionsVO();
        vo.setFromStatus(fromStatus);
        vo.setTargets(all.stream()
                .filter(s -> allowedCodes.contains(s.getStatusCode()))
                .toList());
        return vo;
    }

    public String initialStatus(Long projectId, String typeCode) {
        return getWorkflow(projectId, typeCode).getStatuses().stream()
                .filter(s -> !ANY_STATUS_CODE.equals(s.getStatusCode()))
                .filter(s -> s.getIsInitial() != null && s.getIsInitial() == 1)
                .map(StatusDefinitionVO::getStatusCode)
                .findFirst()
                .orElse("open");
    }

    public void validateTransition(Long projectId, String typeCode, String fromStatus, String toStatus) {
        if (StringUtils.isBlank(toStatus)) {
            throw new IllegalArgumentException("目标状态不能为空");
        }
        if (StringUtils.equals(fromStatus, toStatus)) {
            return;
        }
        StatusWorkflowVO workflow = getWorkflow(projectId, typeCode);
        Set<String> regularCodes = workflow.getStatuses().stream()
                .map(StatusDefinitionVO::getStatusCode)
                .filter(code -> !ANY_STATUS_CODE.equals(code))
                .collect(Collectors.toSet());
        if (!regularCodes.contains(toStatus)) {
            throw new IllegalArgumentException("目标状态不存在: " + toStatus);
        }
        if (StringUtils.isBlank(fromStatus)) {
            return;
        }
        if (!regularCodes.contains(fromStatus)) {
            throw new IllegalArgumentException("当前状态不存在: " + fromStatus);
        }
        AllowedTransitionsVO allowed = allowedTransitions(projectId, typeCode, fromStatus);
        boolean ok = allowed.getTargets().stream().anyMatch(t -> toStatus.equals(t.getStatusCode()));
        if (!ok) {
            throw new IllegalArgumentException("不允许从「" + labelOf(workflow, fromStatus) + "」流转到「" + labelOf(workflow, toStatus) + "」");
        }
    }

    @Transactional
    public void saveWorkflow(Long projectId, String typeCode, List<StatusDefinitionVO> statuses) {
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个状态");
        }
        validateWorkflowPayload(statuses);
        statusDefinitionMapper.delete(Wrappers.<PmStatusDefinition>lambdaQuery()
                .eq(PmStatusDefinition::getProjectId, projectId)
                .eq(PmStatusDefinition::getTypeCode, typeCode));
        int order = 1;
        for (StatusDefinitionVO item : statuses) {
            PmStatusDefinition row = new PmStatusDefinition();
            row.setProjectId(projectId);
            row.setTypeCode(typeCode);
            row.setStatusCode(item.getStatusCode().trim());
            row.setStatusName(item.getStatusName().trim());
            row.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            row.setIsInitial(item.getIsInitial() != null ? item.getIsInitial() : 0);
            row.setIsFinal(item.getIsFinal() != null ? item.getIsFinal() : 0);
            row.setTransitions(writeTransitions(normalizeTransitions(item.getTransitions())));
            statusDefinitionMapper.insert(row);
            order++;
        }
    }

    @Transactional
    public void resetWorkflow(Long projectId, String typeCode) {
        statusDefinitionMapper.delete(Wrappers.<PmStatusDefinition>lambdaQuery()
                .eq(PmStatusDefinition::getProjectId, projectId)
                .eq(PmStatusDefinition::getTypeCode, typeCode));
    }

    private void validateWorkflowPayload(List<StatusDefinitionVO> statuses) {
        Set<String> codes = new HashSet<>();
        int initialCount = 0;
        Set<String> regularCodes = new HashSet<>();
        for (StatusDefinitionVO item : statuses) {
            if (StringUtils.isBlank(item.getStatusCode()) || StringUtils.isBlank(item.getStatusName())) {
                throw new IllegalArgumentException("状态编码与名称不能为空");
            }
            String code = item.getStatusCode().trim();
            if (!codes.add(code)) {
                throw new IllegalArgumentException("状态编码重复: " + code);
            }
            if (!ANY_STATUS_CODE.equals(code)) {
                regularCodes.add(code);
                if (item.getIsInitial() != null && item.getIsInitial() == 1) {
                    initialCount++;
                }
            }
        }
        if (regularCodes.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个常规状态");
        }
        if (initialCount != 1) {
            throw new IllegalArgumentException("必须且只能有一个初始状态");
        }
        for (StatusDefinitionVO item : statuses) {
            if (item.getTransitions() == null) {
                continue;
            }
            for (String target : item.getTransitions()) {
                if (!regularCodes.contains(target)) {
                    throw new IllegalArgumentException("流转目标不存在: " + target);
                }
                if (Objects.equals(item.getStatusCode(), target)) {
                    throw new IllegalArgumentException("状态不能流转到自身: " + target);
                }
            }
        }
    }

    private List<PmStatusDefinition> listByScope(Long projectId, String typeCode) {
        return statusDefinitionMapper.selectList(Wrappers.<PmStatusDefinition>lambdaQuery()
                        .eq(projectId != null, PmStatusDefinition::getProjectId, projectId)
                        .isNull(projectId == null, PmStatusDefinition::getProjectId)
                        .eq(PmStatusDefinition::getTypeCode, typeCode)
                        .orderByAsc(PmStatusDefinition::getSortOrder))
                .stream()
                .sorted(Comparator.comparingInt(s -> s.getSortOrder() == null ? 0 : s.getSortOrder()))
                .toList();
    }

    private List<PmStatusDefinition> defaultStatuses(String typeCode) {
        List<PmStatusDefinition> list = new ArrayList<>();
        list.add(row(null, typeCode, "open", "待处理", 1, 1, 0, List.of("in_progress", "closed")));
        list.add(row(null, typeCode, "in_progress", "进行中", 2, 0, 0, List.of("done", "open")));
        list.add(row(null, typeCode, "done", "已完成", 3, 0, 0, List.of("closed")));
        list.add(row(null, typeCode, "closed", "已关闭", 4, 0, 1, List.of()));
        list.add(row(null, typeCode, ANY_STATUS_CODE, "任何状态", 99, 0, 0, List.of()));
        return list;
    }

    private PmStatusDefinition row(Long projectId, String typeCode, String code, String name, int order,
                                   int initial, int fin, List<String> transitions) {
        PmStatusDefinition def = new PmStatusDefinition();
        def.setProjectId(projectId);
        def.setTypeCode(typeCode);
        def.setStatusCode(code);
        def.setStatusName(name);
        def.setSortOrder(order);
        def.setIsInitial(initial);
        def.setIsFinal(fin);
        def.setTransitions(writeTransitions(transitions));
        return def;
    }

    private List<StatusDefinitionVO> toVoList(List<PmStatusDefinition> rows) {
        List<StatusDefinitionVO> list = new ArrayList<>();
        for (PmStatusDefinition row : rows) {
            StatusDefinitionVO vo = new StatusDefinitionVO();
            vo.setId(row.getId());
            vo.setStatusCode(row.getStatusCode());
            vo.setStatusName(row.getStatusName());
            vo.setSortOrder(row.getSortOrder());
            vo.setIsInitial(row.getIsInitial());
            vo.setIsFinal(row.getIsFinal());
            vo.setTransitions(parseTransitions(row.getTransitions()));
            list.add(vo);
        }
        if (list.stream().noneMatch(s -> ANY_STATUS_CODE.equals(s.getStatusCode()))) {
            StatusDefinitionVO any = new StatusDefinitionVO();
            any.setStatusCode(ANY_STATUS_CODE);
            any.setStatusName("任何状态");
            any.setSortOrder(999);
            any.setIsInitial(0);
            any.setIsFinal(0);
            any.setTransitions(new ArrayList<>());
            list.add(any);
        }
        return list;
    }

    private StatusDefinitionVO findStatus(List<StatusDefinitionVO> statuses, String code) {
        return statuses.stream()
                .filter(s -> code.equals(s.getStatusCode()))
                .findFirst()
                .orElse(null);
    }

    private String labelOf(StatusWorkflowVO workflow, String code) {
        StatusDefinitionVO status = findStatus(workflow.getStatuses(), code);
        return status != null ? status.getStatusName() : code;
    }

    private List<String> normalizeTransitions(List<String> transitions) {
        if (transitions == null || transitions.isEmpty()) {
            return new ArrayList<>();
        }
        return transitions.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> parseTransitions(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof String text) {
            if (StringUtils.isBlank(text)) {
                return new ArrayList<>();
            }
            try {
                return objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private String writeTransitions(List<String> transitions) {
        try {
            return objectMapper.writeValueAsString(transitions != null ? transitions : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }
}
