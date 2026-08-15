package com.hfwas.devops.apitest.environment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.environment.entity.EnvironmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 环境 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface EnvironmentMapper extends BaseMapper<EnvironmentEntity> {

}