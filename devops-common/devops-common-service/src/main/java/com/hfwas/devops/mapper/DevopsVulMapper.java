package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsVul;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DevopsVulMapper extends BaseMapper<DevopsVul> {

    DevopsVul selectById(Long id);

    List<DevopsVul> list(@Param("ids") List<Long> ids);

    List<DevopsVul> listByGhsa(@Param("ids") List<String> ids);

    boolean save(@Param("devopsVul")DevopsVul devopsVul);

    boolean saveBatch(@Param("devopsVuls")List<DevopsVul> devopsVuls);

    boolean update(@Param("devopsVul")DevopsVul devopsVul);

    boolean updateBatch(@Param("devopsVuls")List<DevopsVul> devopsVuls);

}
