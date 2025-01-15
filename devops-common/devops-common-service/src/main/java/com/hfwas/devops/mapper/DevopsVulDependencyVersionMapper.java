package com.hfwas.devops.mapper;

import com.hfwas.devops.entity.DevopsVulDependencyVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/1/15
 */
@Mapper
public interface DevopsVulDependencyVersionMapper {

    boolean save(@Param("devopsVulDependencyVersion") DevopsVulDependencyVersion devopsVulDependencyVersion);

    boolean saveBatch(@Param("devopsVulDependencyVersions") List<DevopsVulDependencyVersion> devopsVulDependencyVersions);

}
