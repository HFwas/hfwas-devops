package com.hfwas.devops.apitest.apidefine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口版本记录 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface ApiDefinitionVersionMapper extends BaseMapper<ApiDefinitionVersionEntity> {

}