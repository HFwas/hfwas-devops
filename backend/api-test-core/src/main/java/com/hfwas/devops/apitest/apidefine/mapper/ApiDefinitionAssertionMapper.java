package com.hfwas.devops.apitest.apidefine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionAssertionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口断言 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface ApiDefinitionAssertionMapper extends BaseMapper<ApiDefinitionAssertionEntity> {

}