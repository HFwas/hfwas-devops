package com.hfwas.devops.apitest.apidefine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分组 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface ApiGroupMapper extends BaseMapper<ApiGroupEntity> {

}