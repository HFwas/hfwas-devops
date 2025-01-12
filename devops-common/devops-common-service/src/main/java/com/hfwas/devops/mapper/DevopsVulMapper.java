package com.hfwas.devops.mapper;

import com.hfwas.devops.entity.DevopsVul;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DevopsVulMapper{

    DevopsVul selectById(Long id);

    List<DevopsVul> list(@Param("ids") List<Long> ids);

    List<DevopsVul> listByGhsa(@Param("ids") List<String> ids);

    boolean save(@Param("devopsVul")DevopsVul devopsVul);

    boolean saveBatch(@Param("devopsVuls")List<DevopsVul> devopsVuls);

}
