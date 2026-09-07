package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineCredentialEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineCredentialMapper extends BaseMapper<PipelineCredentialEntity> {
}
