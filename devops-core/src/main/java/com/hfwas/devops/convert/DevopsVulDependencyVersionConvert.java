package com.hfwas.devops.convert;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.entity.DevopsVulCodeDependencyVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/1/15
 */
@Mapper
public interface DevopsVulDependencyVersionConvert {

    DevopsVulDependencyVersionConvert INSTANCE = Mappers.getMapper(DevopsVulDependencyVersionConvert.class);

    @Mapping(source = "id", target = "depenId")
    DevopsVulCodeDependencyVersion to(DevopsVulCodeDependency devopsVulDependency);

    @Mapping(source = "id", target = "depenId")
    List<DevopsVulCodeDependencyVersion> tos(List<DevopsVulCodeDependency> devopsVulDependency);

}
