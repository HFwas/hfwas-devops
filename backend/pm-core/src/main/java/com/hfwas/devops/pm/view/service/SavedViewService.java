package com.hfwas.devops.pm.view.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.view.entity.PmSavedView;
import com.hfwas.devops.pm.view.mapper.PmSavedViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final PmSavedViewMapper savedViewMapper;

    public Long save(PmSavedView view) {
        if (view.getId() == null) {
            savedViewMapper.insert(view);
        } else {
            savedViewMapper.updateById(view);
        }
        return view.getId();
    }

    public List<PmSavedView> list(Long projectId, String typeCode) {
        return savedViewMapper.selectList(Wrappers.<PmSavedView>lambdaQuery()
                .eq(PmSavedView::getProjectId, projectId)
                .eq(typeCode != null, PmSavedView::getTypeCode, typeCode)
                .orderByDesc(PmSavedView::getUpdateTime));
    }

    public void delete(Long id) {
        savedViewMapper.deleteById(id);
    }
}
