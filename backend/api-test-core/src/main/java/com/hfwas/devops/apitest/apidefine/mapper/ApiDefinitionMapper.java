package com.hfwas.devops.apitest.apidefine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口定义 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface ApiDefinitionMapper extends BaseMapper<ApiDefinitionEntity> {

}