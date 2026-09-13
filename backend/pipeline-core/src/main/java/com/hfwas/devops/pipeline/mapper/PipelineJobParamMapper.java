package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineJobParamEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PipelineJobParamMapper extends BaseMapper<PipelineJobParamEntity> {

    @Select("SELECT * FROM pipeline_job_param WHERE pipeline_id = #{pipelineId} AND deleted = 0 ORDER BY sort_order")
    List<PipelineJobParamEntity> selectByPipelineId(Long pipelineId);

    @Select("SELECT * FROM pipeline_job_param WHERE job_id = #{jobId} AND deleted = 0 ORDER BY sort_order")
    List<PipelineJobParamEntity> selectByJobId(Long jobId);

    @Select("SELECT * FROM pipeline_job_param WHERE id = #{id} AND deleted = 0")
    PipelineJobParamEntity selectById(Long id);
}