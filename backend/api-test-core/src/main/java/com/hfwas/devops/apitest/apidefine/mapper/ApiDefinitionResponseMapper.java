package com.hfwas.devops.apitest.apidefine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionResponseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口响应定义 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface ApiDefinitionResponseMapper extends BaseMapper<ApiDefinitionResponseEntity> {

}