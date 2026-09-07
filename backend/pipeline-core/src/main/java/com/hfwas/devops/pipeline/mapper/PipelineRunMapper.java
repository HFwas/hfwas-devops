package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineRunMapper extends BaseMapper<PipelineRunEntity> {
}
