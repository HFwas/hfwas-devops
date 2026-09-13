package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PipelineTaskKindParamMapper extends BaseMapper<PipelineTaskKindParamEntity> {

    @Select("SELECT * FROM pipeline_task_kind_param WHERE kind_value = #{kindValue} AND deleted = 0 ORDER BY sort_order")
    List<PipelineTaskKindParamEntity> selectByKindValue(String kindValue);

    @Select("SELECT * FROM pipeline_task_kind_param WHERE deleted = 0 ORDER BY kind_value, sort_order")
    List<PipelineTaskKindParamEntity> selectAll();

    @Delete("UPDATE pipeline_task_kind_param SET deleted = 1 WHERE kind_value = #{kindValue} AND deleted = 0")
    int softDeleteByKindValue(String kindValue);
}
