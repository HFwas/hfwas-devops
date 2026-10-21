package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.mapper.FieldDefinitionMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.project.service.ProjectAccessGuard;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectIssueTypeService {

    private final PmProjectIssueTypeMapper projectIssueTypeMapper;
    private final PmMetaService metaService;
    private final ProjectAccessGuard projectAccessGuard;
    private final PmWorkItemMapper workItemMapper;
    private final FieldDefinitionMapper fieldDefinitionMapper;

    public List<PmWorkItemType> listForProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        projectAccessGuard.assertAccess(projectId);
        List<PmProjectIssueType> scheme = projectIssueTypeMapper.selectList(
                Wrappers.<PmProjectIssueType>lambdaQuery()
                        .eq(PmProjectIssueType::getProjectId, projectId)
                        .orderByAsc(PmProjectIssueType::getSortOrder));
        List<PmWorkItemType> enabled = metaService.listTypes(false);
        List<PmWorkItemType> result;
        if (scheme.isEmpty()) {
            result = new ArrayList<>(enabled);
        } else {
            Map<String, PmWorkItemType> byCode = enabled.stream()
                    .collect(Collectors.toMap(PmWorkItemType::getCode, t -> t, (a, b) -> a));
            result = new ArrayList<>();
            for (PmProjectIssueType row : scheme) {
                PmWorkItemType type = byCode.get(row.getTypeCode());
                if (type != null) {
                    result.add(type);
                }
            }
        }
        enrichCounts(projectId, result);
        return result;
    }

    private void enrichCounts(Long projectId, List<PmWorkItemType> types) {
        if (types.isEmpty()) {
            return;
        }
        Map<String, Long> itemCounts = workItemMapper.countByType(projectId).stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("typeCode")),
                        row -> ((Number) row.get("cnt")).longValue(),
                        (a, b) -> a));
        List<FieldDefinition> customFields = fieldDefinitionMapper.selectList(
                Wrappers.<FieldDefinition>lambdaQuery()
                        .eq(FieldDefinition::getProjectId, projectId)
                        .eq(FieldDefinition::getDelFlag, 0));
        for (PmWorkItemType type : types) {
            type.setWorkItemCount(itemCounts.getOrDefault(type.getCode(), 0L));
            long applicable = customFields.stream()
                    .filter(def -> def.getApplicableTypes() != null
                            && def.getApplicableTypes().contains(type.getCode()))
                    .count();
            type.setFieldCount((long) FieldDefinitionService.SYSTEM_FIELD_COUNT + applicable);
        }
    }

    @Transactional
    public void saveScheme(Long projectId, List<String> typeCodes) {
        projectAccessGuard.assertAccess(projectId);
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        if (typeCodes == null || typeCodes.isEmpty()) {
            throw new IllegalArgumentException("至少启用一个事项类型");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : typeCodes) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String code = raw.trim();
            PmWorkItemType type = metaService.getByCode(code);
            if (type == null || type.getEnabled() == null || type.getEnabled() != 1) {
                throw new IllegalArgumentException("无法启用未启用或不存在的类型: " + code);
            }
            unique.add(code);
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("至少启用一个事项类型");
        }
        projectIssueTypeMapper.delete(Wrappers.<PmProjectIssueType>lambdaQuery()
                .eq(PmProjectIssueType::getProjectId, projectId));
        int order = 1;
        for (String code : unique) {
            PmProjectIssueType row = new PmProjectIssueType();
            row.setProjectId(projectId);
            row.setTypeCode(code);
            row.setSortOrder(order++);
            projectIssueTypeMapper.insert(row);
        }
    }

    @Transactional
    public void seedDefaultScheme(Long projectId) {
        if (projectId == null) {
            return;
        }
        Long existing = projectIssueTypeMapper.selectCount(Wrappers.<PmProjectIssueType>lambdaQuery()
                .eq(PmProjectIssueType::getProjectId, projectId));
        if (existing != null && existing > 0) {
            return;
        }
        List<String> codes = metaService.listTypes(false).stream()
                .map(PmWorkItemType::getCode)
                .filter(Objects::nonNull)
                .toList();
        if (!codes.isEmpty()) {
            saveScheme(projectId, codes);
        }
    }
}
