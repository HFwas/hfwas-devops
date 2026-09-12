package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DependencyComponentMapper extends BaseMapper<DependencyComponentEntity> {
}