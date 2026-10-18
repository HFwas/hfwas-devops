package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.engine.FieldValidator;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.meta.PmMetaService;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.query.engine.QueryEngine;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.spi.registry.WorkItemTypeRegistry;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemLink;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemLinkMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.user.message.MessageCategories;
import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    private final TransitionPostFunctionExecutor transitionPostFunctionExecutor;
    private final TransitionValidatorExecutor transitionValidatorExecutor;
    private final TransitionConditionEvaluator transitionConditionEvaluator;
    private final WorkItemFieldApplicator workItemFieldApplicator;
    private final PmMetaService metaService;
    private final ProjectService projectService;

    public IPage<PmWorkItem> page(QuerySpec spec) {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (spec.getProjectId() != null) {
            projectService.assertProjectAccess(spec.getProjectId());
        } else if (tenantId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        IPage<PmWorkItem> page = queryEngine.execute(spec, tenantId);
        keyEnricher.enrich(page);
        return page;
    }

    public PmWorkItem getById(Long id) {
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(item.getProjectId());
        keyEnricher.enrich(item);
        return item;
    }

    @Transactional
    public Long save(PmWorkItem item) {
        PmWorkItem old = null;
        if (item.getId() == null) {
            if (item.getProjectId() == null) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            projectService.assertProjectAccess(item.getProjectId());
        } else {
            old = workItemMapper.selectById(item.getId());
            if (old == null) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            projectService.assertProjectAccess(old.getProjectId());
            if (item.getProjectId() != null && !item.getProjectId().equals(old.getProjectId())) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            item.setProjectId(old.getProjectId());
        }

        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(
                item.getProjectId(), item.getTypeCode());
        Map<String, Object> normalized = fieldValidator.validateAndNormalize(item, definitions);
        item.setCustomFields(normalized);

        if (old == null) {
            metaService.assertTypeUsableInProject(item.getProjectId(), item.getTypeCode());
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnCreate(item));
            if (item.getStatus() == null || item.getStatus().isBlank()) {
                item.setStatus(statusDefinitionService.initialStatus(item.getProjectId(), item.getTypeCode()));
            }
            item.setItemNo(sequenceService.nextItemNo(item.getProjectId()));
            workItemMapper.insert(item);
            notifyAssigneeIfChanged(new PmWorkItem(), item);
            activityService.recordCreate(item);
        } else {
            final PmWorkItem existing = old;
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnUpdate(existing, item));
            if (item.getStatus() != null && !item.getStatus().equals(old.getStatus())) {
                throw new IllegalArgumentException("请通过 transition API 变更状态（需指定 transitionId）");
            }
            workItemMapper.updateById(item);
            notifyAssigneeIfChanged(old, item);
            activityService.recordChanges(old, item, definitions);
        }
        keyEnricher.enrich(item);
        return item.getId();
    }

    @Transactional
    public void transition(Long id, String transitionId) {
        transition(id, transitionId, null);
    }

    @Transactional
    public void transition(Long id, String transitionId, Map<String, Object> fields) {
        if (StringUtils.isBlank(transitionId)) {
            throw new IllegalArgumentException("transitionId 不能为空");
        }
        PmWorkItem old = workItemMapper.selectById(id);
        if (old == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(old.getProjectId());
        PmWorkItem before = snapshot(old);
        String fromStatus = before.getStatus();
        statusDefinitionService.validateTransition(
                old.getProjectId(), old.getTypeCode(), fromStatus, transitionId);
        TransitionVO transition = statusDefinitionService.findTransition(
                old.getProjectId(), old.getTypeCode(), fromStatus, transitionId);
        transitionConditionEvaluator.assertMatches(old, transition.getConditions());
        String toStatus = transition.getToStatus();
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(
                old.getProjectId(), old.getTypeCode());
        if (fields != null && !fields.isEmpty()) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                if (entry.getKey() == null || "status".equals(entry.getKey())) {
                    continue;
                }
                workItemFieldApplicator.apply(old, entry.getKey(), entry.getValue(), definitions);
            }
        }
        transitionValidatorExecutor.validate(old, fromStatus, transitionId, definitions);
        old.setStatus(toStatus);
        workItemMapper.updateById(old);
        transitionPostFunctionExecutor.execute(old, fromStatus, transitionId, definitions);
        workItemMapper.updateById(old);
        keyEnricher.enrich(old);
        activityService.recordChanges(before, old, definitions);
    }

    public void delete(Long id) {
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(item.getProjectId());
        workItemMapper.deleteById(id);
    }

    @Transactional
    public Long addLink(Long sourceId, Long targetId, String linkType) {
        PmWorkItem source = workItemMapper.selectById(sourceId);
        PmWorkItem target = workItemMapper.selectById(targetId);
        if (source == null || target == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(source.getProjectId());
        projectService.assertProjectAccess(target.getProjectId());
        PmWorkItemLink link = new PmWorkItemLink();
        link.setSourceId(sourceId);
        link.setTargetId(targetId);
        link.setLinkType(linkType);
        linkMapper.insert(link);
        activityService.recordLinkAdd(sourceId, targetId, linkType);
        return link.getId();
    }

    public List<PmWorkItemLink> listLinks(Long workItemId) {
        PmWorkItem item = workItemMapper.selectById(workItemId);
        if (item == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(item.getProjectId());
        return linkMapper.selectList(Wrappers.<PmWorkItemLink>lambdaQuery()
                .eq(PmWorkItemLink::getSourceId, workItemId)
                .or(w -> w.eq(PmWorkItemLink::getTargetId, workItemId)));
    }

    public List<PmWorkItem> listByStatus(Long projectId, String typeCode, String status) {
        projectService.assertProjectAccess(projectId);
        return listByStatusInternal(projectId, typeCode, status);
    }

    /** Board entry: caller has already asserted project access once. */
    public List<PmWorkItem> listByStatusForBoard(Long projectId, String typeCode, String status) {
        return listByStatusInternal(projectId, typeCode, status);
    }

    private List<PmWorkItem> listByStatusInternal(Long projectId, String typeCode, String status) {
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
        if (item.getCustomFields() != null) {
            copy.setCustomFields(new HashMap<>(item.getCustomFields()));
        }
        return copy;
    }
}
