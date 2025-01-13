package com.hfwas.devops.mapper;

import com.hfwas.devops.entity.DevopsVulDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.mapper
 * @date 2025/1/13
 */
@Mapper
public interface DevopsVulDependencyMapper {

    boolean save(@Param("devopsVulDependency") DevopsVulDependency devopsVulDependency);

    boolean saveBatch(@Param("devopsVulDependencys") List<DevopsVulDependency> devopsVulDependencys);

}
