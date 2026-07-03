package com.hfwas.devops.pm.workitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PmWorkItemMapper extends BaseMapper<PmWorkItem> {
}
