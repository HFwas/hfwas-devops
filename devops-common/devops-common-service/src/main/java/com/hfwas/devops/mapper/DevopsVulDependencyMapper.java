package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.mapper
 * @date 2025/1/13
 */
@Mapper
public interface DevopsVulDependencyMapper extends BaseMapper<DevopsVulCodeDependency> {

    DevopsVulCodeDependency getOne(@Param("devopsVulDependency") DevopsVulCodeDependency devopsVulDependency);

    List<DevopsVulCodeDependency> list(@Param("devopsVulDependencys") List<DevopsVulCodeDependency> devopsVulDependencys);

    boolean save(@Param("devopsVulDependency") DevopsVulCodeDependency devopsVulDependency);

    boolean saveBatch(@Param("devopsVulDependencys") List<DevopsVulCodeDependency> devopsVulDependencys);

}
