package com.hfwas.devops.pm.view.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.view.entity.PmSavedView;
import com.hfwas.devops.pm.view.mapper.PmSavedViewMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final PmSavedViewMapper savedViewMapper;
    private final ProjectService projectService;
    private final CurrentUserAccessor currentUserAccessor;

    public Long save(PmSavedView view) {
        if (view.getId() == null) {
            if (view.getProjectId() == null) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            projectService.assertProjectAccess(view.getProjectId());
            view.setUserId(currentUserAccessor.currentUserId());
            savedViewMapper.insert(view);
        } else {
            PmSavedView old = savedViewMapper.selectById(view.getId());
            if (old == null) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            projectService.assertProjectAccess(old.getProjectId());
            if (view.getProjectId() != null && !view.getProjectId().equals(old.getProjectId())) {
                throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
            }
            view.setProjectId(old.getProjectId());
            view.setUserId(old.getUserId());
            savedViewMapper.updateById(view);
        }
        return view.getId();
    }

    public List<PmSavedView> list(Long projectId, String typeCode) {
        projectService.assertProjectAccess(projectId);
        return savedViewMapper.selectList(Wrappers.<PmSavedView>lambdaQuery()
                .eq(PmSavedView::getProjectId, projectId)
                .eq(typeCode != null, PmSavedView::getTypeCode, typeCode)
                .orderByDesc(PmSavedView::getUpdateTime));
    }

    public void delete(Long id) {
        PmSavedView old = savedViewMapper.selectById(id);
        if (old == null) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
        projectService.assertProjectAccess(old.getProjectId());
        savedViewMapper.deleteById(id);
    }
}
