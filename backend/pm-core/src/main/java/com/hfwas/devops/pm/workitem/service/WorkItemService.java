package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.engine.FieldValidator;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.query.engine.QueryEngine;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.spi.registry.WorkItemTypeRegistry;
import com.hfwas.devops.pm.workitem.entity.PmStatusDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemLink;
import com.hfwas.devops.pm.workitem.mapper.PmStatusDefinitionMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemLinkMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
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
    private final PmStatusDefinitionMapper statusDefinitionMapper;
    private final FieldDefinitionService fieldDefinitionService;
    private final FieldValidator fieldValidator;
    private final WorkItemTypeRegistry typeRegistry;
    private final QueryEngine queryEngine;

    public IPage<PmWorkItem> page(QuerySpec spec) {
        return queryEngine.execute(spec);
    }

    public PmWorkItem getById(Long id) {
        return workItemMapper.selectById(id);
    }

    @Transactional
    public Long save(PmWorkItem item) {
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(item.getProjectId(), item.getTypeCode());
        Map<String, Object> normalized = fieldValidator.validateAndNormalize(item, definitions);
        item.setCustomFields(normalized);

        if (item.getId() == null) {
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnCreate(item));
            workItemMapper.insert(item);
        } else {
            PmWorkItem old = workItemMapper.selectById(item.getId());
            typeRegistry.get(item.getTypeCode()).ifPresent(p -> p.validateOnUpdate(old, item));
            workItemMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional
    public void transition(Long id, String toStatus) {
        PmWorkItem item = workItemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("Work item not found");
        }
        validateTransition(item, toStatus);
        item.setStatus(toStatus);
        workItemMapper.updateById(item);
    }

    private void validateTransition(PmWorkItem item, String toStatus) {
        List<PmStatusDefinition> statuses = statusDefinitionMapper.selectList(
                Wrappers.<PmStatusDefinition>lambdaQuery()
                        .eq(PmStatusDefinition::getTypeCode, item.getTypeCode())
                        .and(w -> w.eq(PmStatusDefinition::getProjectId, item.getProjectId()).or().isNull(PmStatusDefinition::getProjectId))
        );
        if (statuses.isEmpty()) {
            return;
        }
        PmStatusDefinition current = statuses.stream()
                .filter(s -> s.getStatusCode().equals(item.getStatus()))
                .findFirst().orElse(null);
        if (current != null && current.getTransitions() != null && !current.getTransitions().isEmpty()) {
            if (!current.getTransitions().contains(toStatus)) {
                throw new IllegalArgumentException("不允许从 " + item.getStatus() + " 流转到 " + toStatus);
            }
        }
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
        return link.getId();
    }

    public List<PmWorkItemLink> listLinks(Long workItemId) {
        return linkMapper.selectList(Wrappers.<PmWorkItemLink>lambdaQuery()
                .eq(PmWorkItemLink::getSourceId, workItemId)
                .or(w -> w.eq(PmWorkItemLink::getTargetId, workItemId)));
    }

    public List<PmWorkItem> listByStatus(Long projectId, String typeCode, String status) {
        return workItemMapper.selectList(Wrappers.<PmWorkItem>lambdaQuery()
                .eq(PmWorkItem::getProjectId, projectId)
                .eq(PmWorkItem::getTypeCode, typeCode)
                .eq(PmWorkItem::getStatus, status)
                .orderByDesc(PmWorkItem::getUpdateTime));
    }
}
