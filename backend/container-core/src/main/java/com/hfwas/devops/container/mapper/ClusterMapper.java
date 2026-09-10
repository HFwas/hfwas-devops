package com.hfwas.devops.container.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.container.entity.ClusterEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClusterMapper extends BaseMapper<ClusterEntity> {
}