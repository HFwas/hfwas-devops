package com.hfwas.devops.convert;

import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.entity.DevopsVulDependencyVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/1/15
 */
@Mapper
public interface DevopsVulDependencyVersionConvert {

    DevopsVulDependencyVersionConvert INSTANCE = Mappers.getMapper(DevopsVulDependencyVersionConvert.class);

    @Mapping(source = "id", target = "depenId")
    DevopsVulDependencyVersion to(DevopsVulDependency devopsVulDependency);

}
