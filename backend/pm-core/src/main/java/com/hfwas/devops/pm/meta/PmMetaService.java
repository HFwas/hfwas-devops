package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PmMetaService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");
    private static final String TEMPLATE_TYPE = "task";

    private final PmWorkItemTypeMapper workItemTypeMapper;
    private final PmWorkItemMapper workItemMapper;
    private final PmProjectIssueTypeMapper projectIssueTypeMapper;
    private final StatusDefinitionService statusDefinitionService;

    public List<PmWorkItemType> listTypes() {
        return listTypes(false);
    }

    public List<PmWorkItemType> listTypes(boolean includeDisabled) {
        return workItemTypeMapper.selectList(Wrappers.<PmWorkItemType>lambdaQuery()
                .eq(!includeDisabled, PmWorkItemType::getEnabled, 1)
                .orderByAsc(PmWorkItemType::getSortOrder));
    }

    public PmWorkItemType getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return workItemTypeMapper.selectOne(Wrappers.<PmWorkItemType>lambdaQuery()
                .eq(PmWorkItemType::getCode, code.trim()));
    }

    @Transactional
    public Long saveType(PmWorkItemType input) {
        if (input == null) {
            throw new IllegalArgumentException("类型不能为空");
        }
        String name = StringUtils.trimToEmpty(input.getName());
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("类型名称不能为空");
        }
        if (input.getId() == null) {
            String code = StringUtils.trimToEmpty(input.getCode()).toLowerCase();
            validateNewCode(code);
            if (getByCode(code) != null) {
                throw new IllegalArgumentException("类型编码已存在: " + code);
            }
            PmWorkItemType row = new PmWorkItemType();
            row.setCode(code);
            row.setName(name);
            row.setIcon(StringUtils.trimToNull(input.getIcon()));
            row.setColor(normalizeColor(input.getColor()));
            row.setSortOrder(input.getSortOrder() != null ? input.getSortOrder() : nextSortOrder());
            row.setEnabled(input.getEnabled() != null ? input.getEnabled() : 1);
            workItemTypeMapper.insert(row);
            statusDefinitionService.cloneSystemWorkflow(TEMPLATE_TYPE, code);
            return row.getId();
        }
        PmWorkItemType existing = workItemTypeMapper.selectById(input.getId());
        if (existing == null) {
            throw new IllegalArgumentException("类型不存在");
        }
        existing.setName(name);
        existing.setIcon(StringUtils.trimToNull(input.getIcon()));
        existing.setColor(normalizeColor(input.getColor() != null ? input.getColor() : existing.getColor()));
        if (input.getSortOrder() != null) {
            existing.setSortOrder(input.getSortOrder());
        }
        if (input.getEnabled() != null) {
            existing.setEnabled(input.getEnabled());
        }
        workItemTypeMapper.updateById(existing);
        return existing.getId();
    }

    @Transactional
    public void deleteType(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("typeCode 不能为空");
        }
        String typeCode = code.trim();
        PmWorkItemType existing = getByCode(typeCode);
        if (existing == null) {
            throw new IllegalArgumentException("类型不存在: " + typeCode);
        }
        Long count = workItemMapper.selectCount(Wrappers.<PmWorkItem>lambdaQuery()
                .eq(PmWorkItem::getTypeCode, typeCode));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该类型下仍有事项，无法删除（可先停用）");
        }
        projectIssueTypeMapper.delete(Wrappers.<PmProjectIssueType>lambdaQuery()
                .eq(PmProjectIssueType::getTypeCode, typeCode));
        statusDefinitionService.deleteSystemWorkflow(typeCode);
        workItemTypeMapper.deleteById(existing.getId());
    }

    public void assertTypeUsableInProject(Long projectId, String typeCode) {
        if (StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("typeCode 不能为空");
        }
        PmWorkItemType type = getByCode(typeCode.trim());
        if (type == null || type.getEnabled() == null || type.getEnabled() != 1) {
            throw new IllegalArgumentException("事项类型不可用: " + typeCode);
        }
        if (projectId == null) {
            return;
        }
        long schemeCount = projectIssueTypeMapper.selectCount(Wrappers.<PmProjectIssueType>lambdaQuery()
                .eq(PmProjectIssueType::getProjectId, projectId));
        if (schemeCount == 0) {
            return;
        }
        Long hit = projectIssueTypeMapper.selectCount(Wrappers.<PmProjectIssueType>lambdaQuery()
                .eq(PmProjectIssueType::getProjectId, projectId)
                .eq(PmProjectIssueType::getTypeCode, typeCode.trim()));
        if (hit == null || hit == 0) {
            throw new IllegalArgumentException("当前项目未启用事项类型: " + typeCode);
        }
    }

    private void validateNewCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("类型编码不能为空");
        }
        if ("__any__".equals(code) || "status".equals(code)) {
            throw new IllegalArgumentException("保留编码不可用: " + code);
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("类型编码须为小写字母开头，仅含 a-z / 0-9 / _，长度 2–32");
        }
    }

    private String normalizeColor(String color) {
        if (StringUtils.isBlank(color)) {
            return "#64748b";
        }
        String c = color.trim();
        if (!c.matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("颜色须为 #RRGGBB");
        }
        return c.toLowerCase();
    }

    private int nextSortOrder() {
        List<PmWorkItemType> all = workItemTypeMapper.selectList(Wrappers.<PmWorkItemType>lambdaQuery()
                .orderByDesc(PmWorkItemType::getSortOrder)
                .last("LIMIT 1"));
        if (all.isEmpty() || all.get(0).getSortOrder() == null) {
            return 1;
        }
        return all.get(0).getSortOrder() + 1;
    }
}
