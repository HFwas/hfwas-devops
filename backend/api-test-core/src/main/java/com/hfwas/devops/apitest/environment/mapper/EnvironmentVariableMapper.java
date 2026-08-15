package com.hfwas.devops.apitest.environment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.environment.entity.EnvironmentVariableEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 环境变量 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface EnvironmentVariableMapper extends BaseMapper<EnvironmentVariableEntity> {

}