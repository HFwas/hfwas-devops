package com.hfwas.devops.pm.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.common.PmPageRequest;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final PmProjectMapper projectMapper;

    public IPage<PmProject> page(PmPageRequest request, String keyword) {
        Page<PmProject> page = new Page<>(request.getPageNo(), request.getPageSize());
        return projectMapper.selectPage(page, Wrappers.<PmProject>lambdaQuery()
                .like(StringUtils.isNotBlank(keyword), PmProject::getName, keyword)
                .or(StringUtils.isNotBlank(keyword), w -> w.like(PmProject::getCode, keyword))
                .orderByDesc(PmProject::getCreateTime));
    }

    public Long save(PmProject project) {
        if (project.getId() == null) {
            projectMapper.insert(project);
        } else {
            projectMapper.updateById(project);
        }
        return project.getId();
    }

    public PmProject getById(Long id) {
        return projectMapper.selectById(id);
    }

    public void delete(Long id) {
        projectMapper.deleteById(id);
    }
}
