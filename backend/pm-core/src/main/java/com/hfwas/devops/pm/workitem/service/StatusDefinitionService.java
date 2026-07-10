package com.hfwas.devops.pm.workitem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryConditionGroup;
import com.hfwas.devops.pm.query.model.QueryOperator;
import com.hfwas.devops.pm.workitem.entity.PmStatusDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmStatusDefinitionMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.model.StatusWorkflowVO;
import com.hfwas.devops.pm.workitem.model.TransitionConditionSpec;
import com.hfwas.devops.pm.workitem.model.TransitionOptionVO;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionType;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionVO;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorType;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatusDefinitionService {

    public static final String ANY_STATUS_CODE = "__any__";

    private static final Set<String> CONDITION_SYSTEM_FIELDS = Set.of(
            "title", "description", "status", "type_code", "priority", "assignee_id", "reporter_id",
            "parent_id", "project_id", "item_no", "create_time", "update_time", "sprint_id", "module_id"
    );

    private final PmStatusDefinitionMapper statusDefinitionMapper;
    private final PmWorkItemMapper workItemMapper;
    private final TransitionConditionEvaluator transitionConditionEvaluator;
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
        return allowedTransitions(projectId, typeCode, fromStatus, null);
    }

    public AllowedTransitionsVO allowedTransitions(Long projectId, String typeCode, String fromStatus,
                                                   Long workItemId) {
        StatusWorkflowVO workflow = getWorkflow(projectId, typeCode);
        Map<String, String> labelByCode = workflow.getStatuses().stream()
                .filter(s -> s.getStatusCode() != null && !ANY_STATUS_CODE.equals(s.getStatusCode()))
                .collect(Collectors.toMap(
                        StatusDefinitionVO::getStatusCode,
                        StatusDefinitionVO::getStatusName,
                        (a, b) -> a,
                        LinkedHashMap::new));
        Set<String> regularCodes = labelByCode.keySet();
        PmWorkItem workItem = null;
        if (workItemId != null) {
            workItem = workItemMapper.selectById(workItemId);
            if (workItem == null) {
                throw new IllegalArgumentException("Work item not found: " + workItemId);
            }
            if (projectId != null && !projectId.equals(workItem.getProjectId())) {
                throw new IllegalArgumentException("事项不属于该项目");
            }
            if (StringUtils.isNotBlank(typeCode) && !typeCode.equals(workItem.getTypeCode())) {
                throw new IllegalArgumentException("事项类型与请求不一致");
            }
            if (StringUtils.isBlank(fromStatus)) {
                fromStatus = workItem.getStatus();
            }
        }
        List<TransitionOptionVO> options = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(fromStatus)) {
            appendTransitionOptions(options, seenIds,
                    findStatus(workflow.getStatuses(), fromStatus), fromStatus, regularCodes, labelByCode, workItem);
            if (!ANY_STATUS_CODE.equals(fromStatus)) {
                appendTransitionOptions(options, seenIds,
                        findStatus(workflow.getStatuses(), ANY_STATUS_CODE), fromStatus, regularCodes, labelByCode,
                        workItem);
            }
        }
        AllowedTransitionsVO vo = new AllowedTransitionsVO();
        vo.setFromStatus(fromStatus);
        vo.setTransitions(options);
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

    public Map<String, String> statusLabelMap(Long projectId, String typeCode) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (StatusDefinitionVO status : getWorkflow(projectId, typeCode).getStatuses()) {
            if (status.getStatusCode() != null) {
                labels.put(status.getStatusCode(), status.getStatusName());
            }
        }
        return labels;
    }

    public TransitionVO findTransition(Long projectId, String typeCode, String fromStatus, String transitionId) {
        if (StringUtils.isBlank(transitionId)) {
            throw new IllegalArgumentException("transitionId 不能为空");
        }
        StatusWorkflowVO workflow = getWorkflow(projectId, typeCode);
        TransitionVO found = findTransitionInStatus(findStatus(workflow.getStatuses(), fromStatus), transitionId);
        if (found == null && !ANY_STATUS_CODE.equals(fromStatus)) {
            found = findTransitionInStatus(findStatus(workflow.getStatuses(), ANY_STATUS_CODE), transitionId);
        }
        if (found == null) {
            throw new IllegalArgumentException("流转不存在: " + transitionId);
        }
        return found;
    }

    public List<TransitionPostFunctionVO> resolvePostFunctions(Long projectId, String typeCode,
                                                               String fromStatus, String transitionId) {
        if (StringUtils.isBlank(transitionId)) {
            return List.of();
        }
        TransitionVO transition = findTransition(projectId, typeCode, fromStatus, transitionId);
        return transition.getPostFunctions() != null ? transition.getPostFunctions() : List.of();
    }

    public List<TransitionValidatorVO> resolveValidators(Long projectId, String typeCode,
                                                         String fromStatus, String transitionId) {
        if (StringUtils.isBlank(transitionId)) {
            return List.of();
        }
        TransitionVO transition = findTransition(projectId, typeCode, fromStatus, transitionId);
        return transition.getValidators() != null ? transition.getValidators() : List.of();
    }

    public void validateTransition(Long projectId, String typeCode, String fromStatus, String transitionId) {
        TransitionVO transition = findTransition(projectId, typeCode, fromStatus, transitionId);
        if (StringUtils.isBlank(transition.getToStatus())) {
            throw new IllegalArgumentException("流转缺少目标状态: " + transitionId);
        }
        String toStatus = transition.getToStatus().trim();
        StatusWorkflowVO workflow = getWorkflow(projectId, typeCode);
        Set<String> regularCodes = workflow.getStatuses().stream()
                .map(StatusDefinitionVO::getStatusCode)
                .filter(code -> !ANY_STATUS_CODE.equals(code))
                .collect(Collectors.toSet());
        if (!regularCodes.contains(toStatus)) {
            throw new IllegalArgumentException("目标状态不存在: " + toStatus);
        }
        if (StringUtils.isNotBlank(fromStatus) && Objects.equals(fromStatus, toStatus)) {
            throw new IllegalArgumentException("状态不能流转到自身: " + toStatus);
        }
        if (StringUtils.isNotBlank(fromStatus) && !ANY_STATUS_CODE.equals(fromStatus)
                && !regularCodes.contains(fromStatus)) {
            throw new IllegalArgumentException("当前状态不存在: " + fromStatus);
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
        ensureTransitionIdsAndNames(statuses);
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
            row.setTransitions(writeTransitions(item.getTransitions()));
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

    public void ensureTransitionIdsAndNames(List<StatusDefinitionVO> statuses) {
        if (statuses == null) {
            return;
        }
        Map<String, String> labelByCode = statuses.stream()
                .filter(s -> s != null && StringUtils.isNotBlank(s.getStatusCode()))
                .collect(Collectors.toMap(
                        s -> s.getStatusCode().trim(),
                        s -> StringUtils.defaultIfBlank(s.getStatusName(), s.getStatusCode()).trim(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        for (StatusDefinitionVO status : statuses) {
            if (status == null) {
                continue;
            }
            if (status.getTransitions() == null) {
                status.setTransitions(new ArrayList<>());
                continue;
            }
            for (TransitionVO transition : status.getTransitions()) {
                if (transition == null) {
                    continue;
                }
                if (StringUtils.isBlank(transition.getId())) {
                    transition.setId(UUID.randomUUID().toString());
                } else {
                    transition.setId(transition.getId().trim());
                }
                if (StringUtils.isBlank(transition.getName()) && StringUtils.isNotBlank(transition.getToStatus())) {
                    String toCode = transition.getToStatus().trim();
                    String toName = labelByCode.getOrDefault(toCode, toCode);
                    transition.setName("→ " + toName);
                } else if (transition.getName() != null) {
                    transition.setName(transition.getName().trim());
                }
                if (transition.getValidators() == null) {
                    transition.setValidators(new ArrayList<>());
                }
                if (transition.getPostFunctions() == null) {
                    transition.setPostFunctions(new ArrayList<>());
                }
                if (transition.getConditions() == null) {
                    transition.setConditions(new TransitionConditionSpec());
                } else {
                    if (transition.getConditions().getConditions() == null) {
                        transition.getConditions().setConditions(new ArrayList<>());
                    }
                    if (transition.getConditions().getGroups() == null) {
                        transition.getConditions().setGroups(new ArrayList<>());
                    }
                }
            }
        }
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
        Set<String> allTransitionIds = new HashSet<>();
        for (StatusDefinitionVO item : statuses) {
            if (item.getTransitions() == null) {
                item.setTransitions(new ArrayList<>());
                continue;
            }
            String fromCode = item.getStatusCode().trim();
            for (TransitionVO transition : item.getTransitions()) {
                if (transition == null) {
                    throw new IllegalArgumentException("流转不能为空（源状态: " + fromCode + "）");
                }
                if (StringUtils.isBlank(transition.getId())) {
                    throw new IllegalArgumentException("流转 id 不能为空（源状态: " + fromCode + "）");
                }
                if (!allTransitionIds.add(transition.getId().trim())) {
                    throw new IllegalArgumentException("流转 id 重复: " + transition.getId());
                }
                if (StringUtils.isBlank(transition.getToStatus())) {
                    throw new IllegalArgumentException("流转缺少 toStatus（源状态: " + fromCode + "）");
                }
                String toStatus = transition.getToStatus().trim();
                transition.setToStatus(toStatus);
                if (!regularCodes.contains(toStatus)) {
                    throw new IllegalArgumentException("流转目标不存在: " + toStatus);
                }
                if (Objects.equals(fromCode, toStatus)) {
                    throw new IllegalArgumentException("状态不能流转到自身: " + toStatus);
                }
                validateConditions(fromCode, transition);
                validateValidators(fromCode, transition);
                validatePostFunctions(fromCode, transition);
            }
        }
    }

    private void validateConditions(String fromCode, TransitionVO transition) {
        TransitionConditionSpec spec = transition.getConditions();
        if (spec == null || spec.isEmpty()) {
            return;
        }
        String edge = fromCode + " → " + transition.getToStatus();
        if (spec.getConditions() != null) {
            for (int i = 0; i < spec.getConditions().size(); i++) {
                validateQueryCondition(spec.getConditions().get(i), edge + " condition#" + (i + 1));
            }
        }
        if (spec.getGroups() != null) {
            for (int i = 0; i < spec.getGroups().size(); i++) {
                validateQueryGroup(spec.getGroups().get(i), edge + " group#" + (i + 1));
            }
        }
    }

    private void validateQueryGroup(QueryConditionGroup group, String path) {
        if (group == null) {
            throw new IllegalArgumentException("条件组不能为空（" + path + "）");
        }
        if (group.getConditions() != null) {
            for (int i = 0; i < group.getConditions().size(); i++) {
                validateQueryCondition(group.getConditions().get(i), path + ".condition#" + (i + 1));
            }
        }
        if (group.getGroups() != null) {
            for (int i = 0; i < group.getGroups().size(); i++) {
                validateQueryGroup(group.getGroups().get(i), path + ".group#" + (i + 1));
            }
        }
    }

    private void validateQueryCondition(QueryCondition condition, String path) {
        if (condition == null || StringUtils.isBlank(condition.getField())) {
            throw new IllegalArgumentException("条件 field 不能为空（" + path + "）");
        }
        String field = condition.getField().trim();
        condition.setField(field);
        if (field.startsWith("custom.")) {
            if (field.length() <= "custom.".length()) {
                throw new IllegalArgumentException("自定义字段 key 不能为空（" + path + "）");
            }
        } else if (!CONDITION_SYSTEM_FIELDS.contains(field)) {
            throw new IllegalArgumentException("不支持的条件字段: " + field + "（" + path + "）");
        }
        if (condition.getOperator() == null) {
            throw new IllegalArgumentException("条件 operator 不能为空（" + path + "）");
        }
        QueryOperator op = condition.getOperator();
        if (op != QueryOperator.IS_NULL && op != QueryOperator.IS_NOT_NULL && condition.getValue() == null) {
            throw new IllegalArgumentException("条件缺少 value（" + path + "）");
        }
    }

    private void validateValidators(String fromCode, TransitionVO transition) {
        if (transition.getValidators() == null) {
            return;
        }
        String edge = fromCode + " → " + transition.getToStatus();
        for (int i = 0; i < transition.getValidators().size(); i++) {
            TransitionValidatorVO validator = transition.getValidators().get(i);
            if (validator == null || StringUtils.isBlank(validator.getType())) {
                throw new IllegalArgumentException("流转校验 type 不能为空（" + edge + " #" + (i + 1) + "）");
            }
            String type = validator.getType().trim();
            if (!TransitionValidatorType.isKnown(type)) {
                throw new IllegalArgumentException("不支持的流转校验类型: " + type + "（" + edge + "）");
            }
            validator.setType(type);
            if (TransitionValidatorType.REQUIRED_FIELDS.equals(type)) {
                if (validator.getFieldKeys() == null || validator.getFieldKeys().isEmpty()) {
                    throw new IllegalArgumentException("REQUIRED_FIELDS 缺少 fieldKeys（" + edge + "）");
                }
                for (String key : validator.getFieldKeys()) {
                    if (StringUtils.isBlank(key)) {
                        throw new IllegalArgumentException("REQUIRED_FIELDS 含空字段（" + edge + "）");
                    }
                    if ("status".equals(key.trim())) {
                        throw new IllegalArgumentException("不能将 status 配置为流转必填字段（" + edge + "）");
                    }
                }
            }
        }
    }

    private void validatePostFunctions(String fromCode, TransitionVO transition) {
        if (transition.getPostFunctions() == null) {
            return;
        }
        String edge = fromCode + " → " + transition.getToStatus();
        for (int i = 0; i < transition.getPostFunctions().size(); i++) {
            TransitionPostFunctionVO fn = transition.getPostFunctions().get(i);
            if (fn == null || StringUtils.isBlank(fn.getType())) {
                throw new IllegalArgumentException("后置函数 type 不能为空（" + edge + " #" + (i + 1) + "）");
            }
            String type = fn.getType().trim();
            if (!TransitionPostFunctionType.isKnown(type)) {
                throw new IllegalArgumentException("不支持的后置函数类型: " + type + "（" + edge + "）");
            }
            fn.setType(type);
            if (TransitionPostFunctionType.SET_FIELD.equals(type) && StringUtils.isBlank(fn.getFieldKey())) {
                throw new IllegalArgumentException("SET_FIELD 缺少 fieldKey（" + edge + "）");
            }
            if (TransitionPostFunctionType.NOTIFY_USER.equals(type) && fn.getUserId() == null) {
                throw new IllegalArgumentException("NOTIFY_USER 缺少 userId（" + edge + "）");
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
        list.add(row(null, typeCode, "open", "待处理", 1, 1, 0, List.of(
                transition("开始处理", "in_progress"),
                transition("关闭", "closed"))));
        list.add(row(null, typeCode, "in_progress", "进行中", 2, 0, 0, List.of(
                transition("完成", "done"),
                transition("重新打开", "open"))));
        list.add(row(null, typeCode, "done", "已完成", 3, 0, 0, List.of(
                transition("关闭", "closed"))));
        list.add(row(null, typeCode, "closed", "已关闭", 4, 0, 1, List.of()));
        list.add(row(null, typeCode, ANY_STATUS_CODE, "任何状态", 99, 0, 0, List.of()));
        return list;
    }

    private TransitionVO transition(String name, String toStatus) {
        TransitionVO vo = new TransitionVO();
        vo.setId(UUID.randomUUID().toString());
        vo.setName(name);
        vo.setToStatus(toStatus);
        vo.setValidators(new ArrayList<>());
        vo.setPostFunctions(new ArrayList<>());
        vo.setConditions(new TransitionConditionSpec());
        return vo;
    }

    private PmStatusDefinition row(Long projectId, String typeCode, String code, String name, int order,
                                   int initial, int fin, List<TransitionVO> transitions) {
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
        ensureTransitionIdsAndNames(list);
        return list;
    }

    private void appendTransitionOptions(List<TransitionOptionVO> options, Set<String> seenIds,
                                         StatusDefinitionVO status, String fromStatus,
                                         Set<String> regularCodes, Map<String, String> labelByCode,
                                         PmWorkItem workItem) {
        if (status == null || status.getTransitions() == null) {
            return;
        }
        for (TransitionVO transition : status.getTransitions()) {
            if (transition == null || StringUtils.isBlank(transition.getId())
                    || StringUtils.isBlank(transition.getToStatus())) {
                continue;
            }
            String toStatus = transition.getToStatus().trim();
            if (!regularCodes.contains(toStatus)) {
                continue;
            }
            if (Objects.equals(fromStatus, toStatus)) {
                continue;
            }
            TransitionConditionSpec conditions = transition.getConditions();
            boolean hasConditions = conditions != null && !conditions.isEmpty();
            if (workItem == null) {
                if (hasConditions) {
                    continue;
                }
            } else if (!transitionConditionEvaluator.matches(workItem, conditions)) {
                continue;
            }
            if (!seenIds.add(transition.getId())) {
                continue;
            }
            TransitionOptionVO option = new TransitionOptionVO();
            option.setId(transition.getId());
            option.setName(StringUtils.defaultIfBlank(transition.getName(), "→ " + labelByCode.getOrDefault(toStatus, toStatus)));
            option.setToStatus(toStatus);
            option.setToStatusName(labelByCode.getOrDefault(toStatus, toStatus));
            options.add(option);
        }
    }

    private TransitionVO findTransitionInStatus(StatusDefinitionVO status, String transitionId) {
        if (status == null || status.getTransitions() == null || StringUtils.isBlank(transitionId)) {
            return null;
        }
        String id = transitionId.trim();
        for (TransitionVO transition : status.getTransitions()) {
            if (transition != null && id.equals(transition.getId())) {
                return transition;
            }
        }
        return null;
    }

    private StatusDefinitionVO findStatus(List<StatusDefinitionVO> statuses, String code) {
        if (statuses == null || StringUtils.isBlank(code)) {
            return null;
        }
        return statuses.stream()
                .filter(s -> code.equals(s.getStatusCode()))
                .findFirst()
                .orElse(null);
    }

    private List<TransitionVO> parseTransitions(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof String text) {
            if (StringUtils.isBlank(text)) {
                return new ArrayList<>();
            }
            try {
                List<TransitionVO> list = objectMapper.readValue(text, new TypeReference<List<TransitionVO>>() {
                });
                return list != null ? list : new ArrayList<>();
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private String writeTransitions(List<TransitionVO> transitions) {
        try {
            return objectMapper.writeValueAsString(transitions != null ? transitions : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }
}
