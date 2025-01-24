package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsVulCodeDependencyVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/1/15
 */
@Mapper
public interface DevopsVulDependencyVersionMapper extends BaseMapper<DevopsVulCodeDependencyVersion> {

    boolean save(@Param("devopsVulDependencyVersion") DevopsVulCodeDependencyVersion devopsVulDependencyVersion);

    boolean saveBatch(@Param("devopsVulDependencyVersions") List<DevopsVulCodeDependencyVersion> devopsVulDependencyVersions);

}
