package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.BatchFieldChangeVO;
import com.hfwas.devops.pm.workitem.model.TransitionOptionVO;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.pm.workitem.model.WorkItemFieldUpdateVO;
import com.hfwas.devops.pm.workitem.model.WorkItemPatchResult;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkItemFieldUpdateService {

    private static final Set<String> SYSTEM_MUTABLE_FIELDS = Set.of(
            "title", "description", "priority", "assignee_id", "reporter_id",
            "parent_id", "module_id", "sprint_id", "end_time");
    private static final Set<String> PROTECTED_FIELDS = Set.of(
            "id", "project_id", "item_no", "type_code", "status", "version", "custom_fields", "del_flag",
            "create_by", "create_time", "update_by", "update_time");

    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("title", "title"),
            Map.entry("description", "description"),
            Map.entry("priority", "priority"),
            Map.entry("assignee_id", "assignee_id"),
            Map.entry("reporter_id", "reporter_id"),
            Map.entry("parent_id", "parent_id"),
            Map.entry("module_id", "module_id"),
            Map.entry("sprint_id", "sprint_id"),
            Map.entry("end_time", "end_time"));

    private final PmWorkItemMapper workItemMapper;
    private final ProjectService projectService;
    private final FieldDefinitionService fieldDefinitionService;
    private final WorkItemFieldApplicator fieldApplicator;
    private final WorkItemActivityService activityService;
    private final WorkItemFieldDisplayResolver displayResolver;
    private final WorkItemService workItemService;
    private final StatusDefinitionService statusDefinitionService;
    private final CurrentUserAccessor currentUserAccessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 单字段更新：status 必须走工作流流转，其余字段走条件更新。
     */
    @Transactional
    public WorkItemFieldUpdateVO updateField(Long id, String field, Object value,
                                             Long expectedVersion, String transitionId) {
        if (StringUtils.isBlank(field)) {
            throw new BizException(ResultCode.BAD_REQUEST, "field 不能为空");
        }
        if (expectedVersion == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "version 不能为空");
        }
        String fieldKey = field.trim();
        if ("status".equals(fieldKey)) {
            return updateStatus(id, value, expectedVersion, transitionId);
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(fieldKey, value);
        List<BatchFieldChangeVO> changes = applyPatch(id, patch, expectedVersion, null).getChanges();
        PmWorkItem item = workItemService.getById(id);
        WorkItemFieldUpdateVO vo = new WorkItemFieldUpdateVO();
        vo.setItem(item);
        vo.setFieldKey(fieldKey);
        if (!changes.isEmpty()) {
            BatchFieldChangeVO change = changes.get(0);
            vo.setOldValue(change.getOldValue());
            vo.setNewValue(change.getNewValue());
            FieldDefinition def = findDefinition(item, fieldKey);
            if (def != null) {
                vo.setOldLabel(displayResolver.toLabel(item, def, change.getOldValue()));
                vo.setNewLabel(displayResolver.toLabel(item, def, change.getNewValue()));
            }
        }
        return vo;
    }

    /**
     * 批量字段更新核心：合并自定义字段、条件更新并记录活动；batchId 为空时生成新批次。
     */
    @Transactional
    public WorkItemPatchResult applyPatch(Long id, Map<String, Object> patch,
                                          Long expectedVersion, String batchId) {
        Prepared prepared = prepare(id, patch, expectedVersion);
        PmWorkItem item = prepared.item;
        long nextVersion = WorkItemService.nextVersion(prepared.currentVersion);
        UpdateWrapper<PmWorkItem> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("version", prepared.currentVersion)
                .eq("del_flag", 0)
                .set("version", nextVersion)
                .set("update_by", currentUserAccessor.currentUserId())
                .set("update_time", LocalDateTime.now());
        for (String fieldKey : prepared.patch.keySet()) {
            if (COLUMNS.containsKey(fieldKey)) {
                wrapper.set(COLUMNS.get(fieldKey), readSystemValue(item, fieldKey));
            }
        }
        if (prepared.hasCustomChange) {
            wrapper.set("custom_fields", toJson(item.getCustomFields()));
        }
        int updated = workItemMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BizException(ResultCode.WORK_ITEM_VERSION_CONFLICT);
        }
        activityService.recordChanges(prepared.before, item, prepared.definitions,
                StringUtils.defaultIfBlank(batchId, UUID.randomUUID().toString()));
        return new WorkItemPatchResult(prepared.changes, nextVersion);
    }

    /**
     * dry-run：完整校验字段与版本，不写库、不记活动。
     */
    public WorkItemPatchResult previewPatch(Long id, Map<String, Object> patch, Long expectedVersion) {
        Prepared prepared = prepare(id, patch, expectedVersion);
        return new WorkItemPatchResult(prepared.changes,
                WorkItemService.nextVersion(prepared.currentVersion));
    }

    private Prepared prepare(Long id, Map<String, Object> patch, Long expectedVersion) {
        if (patch == null || patch.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少提供一个字段");
        }
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.WORK_ITEM_NOT_FOUND);
        }
        projectService.assertProjectAccess(item.getProjectId());
        long currentVersion = item.getVersion() == null ? 0L : item.getVersion();
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw new BizException(ResultCode.WORK_ITEM_VERSION_CONFLICT);
        }
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(
                item.getProjectId(), item.getTypeCode());
        PmWorkItem before = snapshot(item);
        List<BatchFieldChangeVO> changes = new ArrayList<>();
        boolean hasCustomChange = false;
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String fieldKey = entry.getKey() == null ? null : entry.getKey().trim();
            if (StringUtils.isBlank(fieldKey) || PROTECTED_FIELDS.contains(fieldKey)) {
                throw new BizException(ResultCode.FIELD_UNKNOWN, "字段不可修改: " + fieldKey);
            }
            Object oldValue = readValue(before, fieldKey, definitions);
            boolean custom = !SYSTEM_MUTABLE_FIELDS.contains(fieldKey);
            if (custom) {
                FieldDefinition def = findCustomDefinition(definitions, fieldKey);
                if (def == null) {
                    throw new BizException(ResultCode.FIELD_UNKNOWN, "未知字段: " + fieldKey);
                }
                hasCustomChange = true;
            }
            fieldApplicator.apply(item, fieldKey, entry.getValue(), definitions);
            if ("title".equals(fieldKey) && StringUtils.isBlank(item.getTitle())) {
                throw new BizException(ResultCode.TITLE_REQUIRED);
            }
            if ("parent_id".equals(fieldKey)) {
                assertParentValid(item, item.getParentId());
            }
            Object newValue = readValue(item, fieldKey, definitions);
            changes.add(new BatchFieldChangeVO(fieldKey, oldValue, newValue));
        }
        Prepared prepared = new Prepared();
        prepared.item = item;
        prepared.before = before;
        prepared.definitions = definitions;
        prepared.patch = patch;
        prepared.changes = changes;
        prepared.hasCustomChange = hasCustomChange;
        prepared.currentVersion = currentVersion;
        return prepared;
    }

    private WorkItemFieldUpdateVO updateStatus(Long id, Object value, Long expectedVersion, String transitionId) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            throw new BizException(ResultCode.STATUS_REQUIRED);
        }
        String targetStatus = String.valueOf(value).trim();
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.WORK_ITEM_NOT_FOUND);
        }
        projectService.assertProjectAccess(item.getProjectId());
        if (item.getVersion() == null || !expectedVersion.equals(item.getVersion())) {
            throw new BizException(ResultCode.WORK_ITEM_VERSION_CONFLICT);
        }
        String resolvedTransitionId = StringUtils.trimToNull(transitionId);
        if (resolvedTransitionId == null) {
            AllowedTransitionsVO allowed = statusDefinitionService.allowedTransitions(
                    item.getProjectId(), item.getTypeCode(), item.getStatus(), id);
            List<TransitionOptionVO> candidates = allowed.getTransitions() == null ? List.of()
                    : allowed.getTransitions().stream()
                    .filter(t -> targetStatus.equals(t.getToStatus()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                        "当前状态不允许流转到 " + targetStatus);
            }
            if (candidates.size() > 1) {
                String ids = candidates.stream().map(TransitionOptionVO::getId).collect(Collectors.joining(", "));
                throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                        "存在多个可达流转，请指定 transitionId: " + ids);
            }
            resolvedTransitionId = candidates.get(0).getId();
        } else {
            TransitionVO transition = statusDefinitionService.findTransition(
                    item.getProjectId(), item.getTypeCode(), item.getStatus(), resolvedTransitionId);
            if (!targetStatus.equals(transition.getToStatus())) {
                throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                        "流转目标与目标状态不一致");
            }
        }
        String fromStatus = item.getStatus();
        workItemService.transitionWithVersion(id, resolvedTransitionId, null, expectedVersion);
        PmWorkItem updated = workItemService.getById(id);
        Map<String, String> labels = statusDefinitionService.statusLabelMap(
                updated.getProjectId(), updated.getTypeCode());
        WorkItemFieldUpdateVO vo = new WorkItemFieldUpdateVO();
        vo.setItem(updated);
        vo.setFieldKey("status");
        vo.setOldValue(fromStatus);
        vo.setNewValue(updated.getStatus());
        vo.setOldLabel(labels.getOrDefault(fromStatus, fromStatus));
        vo.setNewLabel(labels.getOrDefault(updated.getStatus(), updated.getStatus()));
        return vo;
    }

    private Object readValue(PmWorkItem item, String fieldKey, List<FieldDefinition> definitions) {
        if (SYSTEM_MUTABLE_FIELDS.contains(fieldKey)) {
            return readSystemValue(item, fieldKey);
        }
        FieldDefinition def = findCustomDefinition(definitions, fieldKey);
        if (def == null || item.getCustomFields() == null) {
            return null;
        }
        return item.getCustomFields().get(fieldKey);
    }

    private Object readSystemValue(PmWorkItem item, String fieldKey) {
        return switch (fieldKey) {
            case "title" -> item.getTitle();
            case "description" -> item.getDescription();
            case "priority" -> item.getPriority();
            case "assignee_id" -> item.getAssigneeId();
            case "reporter_id" -> item.getReporterId();
            case "parent_id" -> item.getParentId();
            case "module_id" -> item.getModuleId();
            case "sprint_id" -> item.getSprintId();
            case "end_time" -> item.getEndTime();
            default -> null;
        };
    }

    private FieldDefinition findDefinition(PmWorkItem item, String fieldKey) {
        return fieldDefinitionService.listByProjectAndType(item.getProjectId(), item.getTypeCode())
                .stream()
                .filter(d -> fieldKey.equals(d.getFieldKey()))
                .findFirst()
                .orElse(null);
    }

    private FieldDefinition findCustomDefinition(List<FieldDefinition> definitions, String fieldKey) {
        if (definitions == null) {
            return null;
        }
        return definitions.stream()
                .filter(d -> fieldKey.equals(d.getFieldKey()))
                .filter(d -> d.getSystemFlag() == null || d.getSystemFlag() == 0)
                .findFirst()
                .orElse(null);
    }

    private void assertParentValid(PmWorkItem item, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(item.getId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "父事项不能是自身");
        }
        Long cursor = parentId;
        int guard = 0;
        while (cursor != null && guard++ < 100) {
            PmWorkItem parent = workItemMapper.selectById(cursor);
            if (parent == null || !item.getProjectId().equals(parent.getProjectId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "父事项不存在或不属于当前项目");
            }
            if (cursor.equals(item.getId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "父子关系不能形成环");
            }
            cursor = parent.getParentId();
        }
    }

    private PmWorkItem snapshot(PmWorkItem item) {
        PmWorkItem copy = new PmWorkItem();
        copy.setId(item.getId());
        copy.setProjectId(item.getProjectId());
        copy.setTypeCode(item.getTypeCode());
        copy.setTitle(item.getTitle());
        copy.setDescription(item.getDescription());
        copy.setStatus(item.getStatus());
        copy.setPriority(item.getPriority());
        copy.setAssigneeId(item.getAssigneeId());
        copy.setReporterId(item.getReporterId());
        copy.setModuleId(item.getModuleId());
        copy.setParentId(item.getParentId());
        copy.setSprintId(item.getSprintId());
        copy.setEndTime(item.getEndTime());
        copy.setVersion(item.getVersion());
        if (item.getCustomFields() != null) {
            copy.setCustomFields(new java.util.HashMap<>(item.getCustomFields()));
        }
        return copy;
    }

    private String toJson(Map<String, Object> customFields) {
        try {
            return objectMapper.writeValueAsString(customFields == null ? Map.of() : customFields);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "自定义字段序列化失败");
        }
    }

    private static final class Prepared {
        private PmWorkItem item;
        private PmWorkItem before;
        private List<FieldDefinition> definitions;
        private Map<String, Object> patch;
        private List<BatchFieldChangeVO> changes;
        private boolean hasCustomChange;
        private long currentVersion;
    }
}
