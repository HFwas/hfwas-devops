package com.hfwas.devops.container.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.container.entity.RegistryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegistryMapper extends BaseMapper<RegistryEntity> {
}