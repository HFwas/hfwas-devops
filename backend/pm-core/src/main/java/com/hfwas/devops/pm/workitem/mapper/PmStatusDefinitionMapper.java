package com.hfwas.devops.pm.workitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pm.workitem.entity.PmStatusDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PmStatusDefinitionMapper extends BaseMapper<PmStatusDefinition> {
}
