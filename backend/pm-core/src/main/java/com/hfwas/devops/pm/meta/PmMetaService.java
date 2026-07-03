package com.hfwas.devops.pm.meta;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PmMetaService {

    private final PmWorkItemTypeMapper workItemTypeMapper;

    public List<PmWorkItemType> listTypes() {
        return workItemTypeMapper.selectList(Wrappers.<PmWorkItemType>lambdaQuery()
                .eq(PmWorkItemType::getEnabled, 1)
                .orderByAsc(PmWorkItemType::getSortOrder));
    }
}
