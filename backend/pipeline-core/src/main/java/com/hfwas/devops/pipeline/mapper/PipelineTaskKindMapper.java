package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PipelineTaskKindMapper extends BaseMapper<PipelineTaskKindEntity> {

    @Select("SELECT * FROM pipeline_task_kind WHERE kind_value = #{kindValue} AND deleted = 0")
    PipelineTaskKindEntity selectByKind(String kindValue);

    @Select("SELECT * FROM pipeline_task_kind WHERE enabled = 1 AND deleted = 0 ORDER BY task_group, sort_order")
    List<PipelineTaskKindEntity> selectEnabled();
}