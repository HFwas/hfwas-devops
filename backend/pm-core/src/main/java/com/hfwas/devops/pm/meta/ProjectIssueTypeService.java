package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
    private final PmWorkItemTypeMapper workItemTypeMapper;
    private final PmMetaService metaService;

    public List<PmWorkItemType> listForProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        List<PmProjectIssueType> scheme = projectIssueTypeMapper.selectList(
                Wrappers.<PmProjectIssueType>lambdaQuery()
                        .eq(PmProjectIssueType::getProjectId, projectId)
                        .orderByAsc(PmProjectIssueType::getSortOrder));
        List<PmWorkItemType> enabled = metaService.listTypes(false);
        if (scheme.isEmpty()) {
            return enabled;
        }
        Map<String, PmWorkItemType> byCode = enabled.stream()
                .collect(Collectors.toMap(PmWorkItemType::getCode, t -> t, (a, b) -> a));
        List<PmWorkItemType> result = new ArrayList<>();
        for (PmProjectIssueType row : scheme) {
            PmWorkItemType type = byCode.get(row.getTypeCode());
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    @Transactional
    public void saveScheme(Long projectId, List<String> typeCodes) {
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
