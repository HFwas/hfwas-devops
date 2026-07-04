package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemActivity;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemActivityMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.WorkItemActivityVo;
import com.hfwas.devops.pm.workitem.model.WorkItemFieldChange;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkItemActivityService {

    public static final String EVENT_CREATE = "CREATE";
    public static final String EVENT_FIELD_CHANGE = "FIELD_CHANGE";
    public static final String EVENT_LINK_ADD = "LINK_ADD";

    private final PmWorkItemActivityMapper activityMapper;
    private final PmWorkItemMapper workItemMapper;
    private final WorkItemChangeDetector changeDetector;
    private final WorkItemFieldDisplayResolver displayResolver;
    private final WorkItemKeyEnricher keyEnricher;
    private final CurrentUserAccessor currentUserAccessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<WorkItemActivityVo> listByWorkItem(Long workItemId) {
        ensureWorkItemExists(workItemId);
        return activityMapper.selectList(Wrappers.<PmWorkItemActivity>lambdaQuery()
                        .eq(PmWorkItemActivity::getWorkItemId, workItemId)
                        .orderByDesc(PmWorkItemActivity::getCreateTime)
                        .orderByDesc(PmWorkItemActivity::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Transactional
    public void recordCreate(PmWorkItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        PmWorkItemActivity row = baseRow(item.getId(), EVENT_CREATE);
        row.setFieldName("工作项");
        activityMapper.insert(row);
    }

    @Transactional
    public void recordChanges(PmWorkItem oldItem, PmWorkItem newItem, List<FieldDefinition> definitions) {
        if (newItem == null || newItem.getId() == null) {
            return;
        }
        List<WorkItemFieldChange> changes = changeDetector.detect(oldItem, newItem, definitions);
        if (changes.isEmpty()) {
            return;
        }
        String batchId = UUID.randomUUID().toString();
        Map<String, FieldDefinition> defByKey = new HashMap<>();
        for (FieldDefinition def : definitions) {
            if (def.getFieldKey() != null) {
                defByKey.put(def.getFieldKey(), def);
            }
        }
        for (WorkItemFieldChange change : changes) {
            FieldDefinition def = defByKey.get(change.getFieldKey());
            if (def == null) {
                continue;
            }
            PmWorkItemActivity row = baseRow(newItem.getId(), EVENT_FIELD_CHANGE);
            row.setBatchId(batchId);
            row.setFieldKey(change.getFieldKey());
            row.setFieldName(change.getFieldName());
            row.setFieldType(change.getFieldType());
            row.setOldValue(displayResolver.serializeValue(change.getOldValue()));
            row.setNewValue(displayResolver.serializeValue(change.getNewValue()));
            row.setOldLabel(displayResolver.toLabel(newItem, def, change.getOldValue()));
            row.setNewLabel(displayResolver.toLabel(newItem, def, change.getNewValue()));
            activityMapper.insert(row);
        }
    }

    @Transactional
    public void recordLinkAdd(Long sourceId, Long targetId, String linkType) {
        PmWorkItem source = workItemMapper.selectById(sourceId);
        PmWorkItem target = workItemMapper.selectById(targetId);
        if (source == null || target == null) {
            return;
        }
        keyEnricher.enrich(target);
        PmWorkItemActivity row = baseRow(sourceId, EVENT_LINK_ADD);
        row.setFieldName("关联");
        row.setFieldType("LINK");
        row.setNewLabel(target.getItemKey() != null ? target.getItemKey() : "#" + target.getItemNo());
        try {
            Map<String, Object> extra = new HashMap<>();
            extra.put("linkType", linkType);
            extra.put("targetId", targetId);
            extra.put("targetKey", target.getItemKey());
            extra.put("targetTitle", target.getTitle());
            row.setExtraJson(objectMapper.writeValueAsString(extra));
        } catch (Exception ignored) {
            row.setExtraJson("{\"targetId\":" + targetId + "}");
        }
        activityMapper.insert(row);
    }

    private PmWorkItemActivity baseRow(Long workItemId, String eventType) {
        PmWorkItemActivity row = new PmWorkItemActivity();
        row.setWorkItemId(workItemId);
        row.setEventType(eventType);
        row.setActorId(currentUserAccessor.currentUserId());
        row.setActorName(currentUserAccessor.currentDisplayName());
        return row;
    }

    private void ensureWorkItemExists(Long workItemId) {
        if (workItemMapper.selectById(workItemId) == null) {
            throw new IllegalArgumentException("事项不存在");
        }
    }

    private WorkItemActivityVo toVo(PmWorkItemActivity row) {
        WorkItemActivityVo vo = new WorkItemActivityVo();
        vo.setId(row.getId());
        vo.setWorkItemId(row.getWorkItemId());
        vo.setBatchId(row.getBatchId());
        vo.setEventType(row.getEventType());
        vo.setActorId(row.getActorId());
        vo.setActorName(row.getActorName());
        vo.setFieldKey(row.getFieldKey());
        vo.setFieldName(row.getFieldName());
        vo.setFieldType(row.getFieldType());
        vo.setOldValue(row.getOldValue());
        vo.setNewValue(row.getNewValue());
        vo.setOldLabel(row.getOldLabel());
        vo.setNewLabel(row.getNewLabel());
        vo.setExtraJson(row.getExtraJson());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
