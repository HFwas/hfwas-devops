package com.hfwas.devops.pm.module.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.module.entity.PmProjectModule;
import com.hfwas.devops.pm.module.mapper.PmProjectModuleMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectModuleService {

    private final PmProjectModuleMapper moduleMapper;
    private final PmWorkItemMapper workItemMapper;

    public List<PmProjectModule> listTree(Long projectId) {
        List<PmProjectModule> all = listEnabledByProject(projectId);
        return buildTree(all);
    }

    public List<PmProjectModule> listFlat(Long projectId) {
        List<PmProjectModule> all = listEnabledByProject(projectId);
        Map<Long, PmProjectModule> byId = all.stream()
                .collect(Collectors.toMap(PmProjectModule::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        List<PmProjectModule> flat = new ArrayList<>();
        for (PmProjectModule module : all) {
            module.setPathLabel(buildPathLabel(module, byId));
            flat.add(module);
        }
        return flat;
    }

    @Transactional
    public Long save(PmProjectModule module) {
        if (module.getProjectId() == null) {
            throw new IllegalArgumentException("项目不能为空");
        }
        if (StringUtils.isBlank(module.getName())) {
            throw new IllegalArgumentException("模块名称不能为空");
        }
        module.setName(module.getName().trim());
        if (module.getParentId() != null && module.getParentId().equals(module.getId())) {
            throw new IllegalArgumentException("上级模块不能是自身");
        }
        if (module.getParentId() != null) {
            PmProjectModule parent = moduleMapper.selectById(module.getParentId());
            if (parent == null || !Objects.equals(parent.getProjectId(), module.getProjectId())) {
                throw new IllegalArgumentException("上级模块不存在");
            }
        }
        if (module.getId() != null && module.getParentId() != null && isDescendant(module.getId(), module.getParentId())) {
            throw new IllegalArgumentException("上级模块不能是当前模块的子模块");
        }
        ensureUniqueName(module);

        if (module.getSortOrder() == null) {
            module.setSortOrder(nextSortOrder(module.getProjectId(), module.getParentId()));
        }
        if (module.getEnabled() == null) {
            module.setEnabled(1);
        }

        if (module.getId() == null) {
            moduleMapper.insert(module);
        } else {
            moduleMapper.updateById(module);
        }
        return module.getId();
    }

    @Transactional
    public void delete(Long id) {
        PmProjectModule module = moduleMapper.selectById(id);
        if (module == null) {
            throw new IllegalArgumentException("模块不存在或已删除");
        }
        Long childCount = moduleMapper.selectCount(Wrappers.<PmProjectModule>lambdaQuery()
                .eq(PmProjectModule::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new IllegalArgumentException("请先删除或移动子模块");
        }
        Long usageCount = workItemMapper.selectCount(Wrappers.<PmWorkItem>lambdaQuery()
                .eq(PmWorkItem::getModuleId, id));
        if (usageCount != null && usageCount > 0) {
            throw new IllegalArgumentException("该模块下仍有 " + usageCount + " 个事项，请先调整归属后再删除");
        }
        moduleMapper.deleteById(id);
    }

    private List<PmProjectModule> listEnabledByProject(Long projectId) {
        return moduleMapper.selectList(Wrappers.<PmProjectModule>lambdaQuery()
                .eq(PmProjectModule::getProjectId, projectId)
                .eq(PmProjectModule::getEnabled, 1)
                .orderByAsc(PmProjectModule::getSortOrder)
                .orderByAsc(PmProjectModule::getId));
    }

    private List<PmProjectModule> buildTree(List<PmProjectModule> all) {
        Map<Long, PmProjectModule> byId = all.stream()
                .collect(Collectors.toMap(PmProjectModule::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        List<PmProjectModule> roots = new ArrayList<>();
        for (PmProjectModule module : all) {
            Long parentId = module.getParentId();
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(module);
                continue;
            }
            byId.get(parentId).getChildren().add(module);
        }
        return roots;
    }

    private String buildPathLabel(PmProjectModule module, Map<Long, PmProjectModule> byId) {
        Deque<String> parts = new ArrayDeque<>();
        PmProjectModule current = module;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            parts.addFirst(current.getName());
            Long parentId = current.getParentId();
            current = parentId == null ? null : byId.get(parentId);
        }
        return String.join(" / ", parts);
    }

    private void ensureUniqueName(PmProjectModule module) {
        Long count = moduleMapper.selectCount(Wrappers.<PmProjectModule>lambdaQuery()
                .eq(PmProjectModule::getProjectId, module.getProjectId())
                .eq(PmProjectModule::getName, module.getName())
                .eq(module.getParentId() != null, PmProjectModule::getParentId, module.getParentId())
                .isNull(module.getParentId() == null, PmProjectModule::getParentId)
                .ne(module.getId() != null, PmProjectModule::getId, module.getId()));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("同级下已存在同名模块");
        }
    }

    private int nextSortOrder(Long projectId, Long parentId) {
        PmProjectModule latest = moduleMapper.selectOne(Wrappers.<PmProjectModule>lambdaQuery()
                .eq(PmProjectModule::getProjectId, projectId)
                .eq(parentId != null, PmProjectModule::getParentId, parentId)
                .isNull(parentId == null, PmProjectModule::getParentId)
                .orderByDesc(PmProjectModule::getSortOrder)
                .last("LIMIT 1"));
        return latest == null || latest.getSortOrder() == null ? 1 : latest.getSortOrder() + 1;
    }

    private boolean isDescendant(Long moduleId, Long candidateParentId) {
        Long current = candidateParentId;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (Objects.equals(current, moduleId)) {
                return true;
            }
            PmProjectModule parent = moduleMapper.selectById(current);
            current = parent == null ? null : parent.getParentId();
        }
        return false;
    }
}
