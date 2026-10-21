package com.hfwas.devops.pm.workitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pm.workitem.entity.PmProjectSequence;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PmProjectSequenceMapper extends BaseMapper<PmProjectSequence> {

    @Select("SELECT project_id, next_item_no FROM pm_project_sequence WHERE project_id = #{projectId} FOR UPDATE")
    PmProjectSequence selectForUpdate(@Param("projectId") Long projectId);
}
