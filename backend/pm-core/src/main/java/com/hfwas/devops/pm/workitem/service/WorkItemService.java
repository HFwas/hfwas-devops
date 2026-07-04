package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.engine.FieldValidator;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.query.engine.QueryEngine;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.spi.registry.WorkItemTypeRegistry;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemLink;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemLinkMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.user.message.MessageCategories;
import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkItemService {

    private final PmWorkItemMapper workItemMapper;
    private final PmWorkItemLinkMapper linkMapper;
    private final FieldDefinitionService fieldDefinitionService;
    private final FieldValidator fieldValidator;
    private final WorkItemTypeRegistry typeRegistry;
    private final QueryEngine queryEngine;
    private final WorkItemSequenceService sequenceService;
    private final WorkItemKeyEnricher keyEnricher;
    private final StatusDefinitionService statusDefinitionService;
    private final SiteMessagePublisher siteMessagePublisher;
    private final CurrentUserAccessor currentUserAccessor;
    private final WorkItemActivityService activityService;

    public IPage<PmWorkItem> page(QuerySpec spec) {
        IPage<PmWorkItem> page = queryEngine.execute(spec);
        keyEnricher.enrich(page);
        return page;
    }

    public PmWorkItem getById(Long id) {
        PmWorkItem item = workItemMapper.selectById(id);
        keyEnricher.enrich(item);
        return item;
    }

    @Transactional
    public Long save(PmWorkItem item) {
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(item.getProjectId(), item.getTypeCode());
        Map<String, Object> normalized = fieldValidator.validateAndNormalize(item, definitions);
        item.setCustomFields(normalized);

        if (item.getId() == null) {
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnCreate(item));
            if (item.getStatus() == null || item.getStatus().isBlank()) {
                item.setStatus(statusDefinitionService.initialStatus(item.getProjectId(), item.getTypeCode()));
            }
            item.setItemNo(sequenceService.nextItemNo(item.getProjectId()));
            workItemMapper.insert(item);
            notifyAssigneeIfChanged(new PmWorkItem(), item);
            activityService.recordCreate(item);
        } else {
            PmWorkItem old = workItemMapper.selectById(item.getId());
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnUpdate(old, item));
            if (old != null && item.getStatus() != null && !item.getStatus().equals(old.getStatus())) {
                statusDefinitionService.validateTransition(
                        item.getProjectId(), item.getTypeCode(), old.getStatus(), item.getStatus());
            }
            workItemMapper.updateById(item);
            notifyAssigneeIfChanged(old, item);
            activityService.recordChanges(old, item, definitions);
        }
        keyEnricher.enrich(item);
        return item.getId();
    }

    @Transactional
    public void transition(Long id, String toStatus) {
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("Work item not found");
        }
        statusDefinitionService.validateTransition(
                item.getProjectId(), item.getTypeCode(), item.getStatus(), toStatus);
        String fromStatus = item.getStatus();
        item.setStatus(toStatus);
        workItemMapper.updateById(item);
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(item.getProjectId(), item.getTypeCode());
        PmWorkItem before = new PmWorkItem();
        before.setId(item.getId());
        before.setProjectId(item.getProjectId());
        before.setTypeCode(item.getTypeCode());
        before.setStatus(fromStatus);
        activityService.recordChanges(before, item, definitions);
    }

    public void delete(Long id) {
        workItemMapper.deleteById(id);
    }

    @Transactional
    public Long addLink(Long sourceId, Long targetId, String linkType) {
        PmWorkItemLink link = new PmWorkItemLink();
        link.setSourceId(sourceId);
        link.setTargetId(targetId);
        link.setLinkType(linkType);
        linkMapper.insert(link);
        activityService.recordLinkAdd(sourceId, targetId, linkType);
        return link.getId();
    }

    public List<PmWorkItemLink> listLinks(Long workItemId) {
        return linkMapper.selectList(Wrappers.<PmWorkItemLink>lambdaQuery()
                .eq(PmWorkItemLink::getSourceId, workItemId)
                .or(w -> w.eq(PmWorkItemLink::getTargetId, workItemId)));
    }

    public List<PmWorkItem> listByStatus(Long projectId, String typeCode, String status) {
        List<PmWorkItem> items = workItemMapper.selectList(Wrappers.<PmWorkItem>lambdaQuery()
                .eq(PmWorkItem::getProjectId, projectId)
                .eq(PmWorkItem::getTypeCode, typeCode)
                .eq(PmWorkItem::getStatus, status)
                .orderByDesc(PmWorkItem::getUpdateTime));
        keyEnricher.enrich(items);
        return items;
    }

    private void notifyAssigneeIfChanged(PmWorkItem old, PmWorkItem item) {
        if (item.getAssigneeId() == null || item.getAssigneeId().equals(old.getAssigneeId())) {
            return;
        }
        Long currentUserId = currentUserAccessor.currentUserId();
        if (item.getAssigneeId().equals(currentUserId)) {
            return;
        }
        siteMessagePublisher.publishToUser(item.getAssigneeId(), SiteMessageCommand.builder()
                .category(MessageCategories.OPERATION)
                .title("工作项已分配给您")
                .content("工作项「" + (item.getTitle() != null ? item.getTitle() : "#" + item.getId()) + "」已分配给您。")
                .tenantId(currentUserAccessor.currentTenantId())
                .bizType("work_item")
                .bizId(String.valueOf(item.getId()))
                .linkUrl("/pm/projects/" + item.getProjectId() + "/items/" + item.getId() + "?type=" + item.getTypeCode())
                .senderId(currentUserId)
                .senderName(currentUserAccessor.currentDisplayName())
                .build());
    }
}
