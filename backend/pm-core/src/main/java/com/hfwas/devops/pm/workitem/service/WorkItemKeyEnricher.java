package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkItemKeyEnricher {

    private final PmProjectMapper projectMapper;

    public void enrich(PmWorkItem item) {
        if (item == null) {
            return;
        }
        item.setItemKey(buildKey(resolveProjectCode(item.getProjectId()), item.getItemNo()));
    }

    public void enrich(List<PmWorkItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, String> codeCache = new HashMap<>();
        for (PmWorkItem item : items) {
            if (item.getProjectId() == null) {
                continue;
            }
            String code = codeCache.computeIfAbsent(item.getProjectId(), this::loadProjectCode);
            item.setItemKey(buildKey(code, item.getItemNo()));
        }
    }

    public void enrich(IPage<PmWorkItem> page) {
        if (page != null) {
            enrich(page.getRecords());
        }
    }

    private String loadProjectCode(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        return project == null ? "ITEM" : project.getCode();
    }

    private String resolveProjectCode(Long projectId) {
        return projectId == null ? "ITEM" : loadProjectCode(projectId);
    }

    static String buildKey(String projectCode, Integer itemNo) {
        if (itemNo == null) {
            return null;
        }
        String code = StringUtils.hasText(projectCode) ? projectCode.trim().toUpperCase() : "ITEM";
        return code + "-" + itemNo;
    }
}
