package com.hfwas.devops.convert;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.entity.DevopsVulCodeDependencyVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/1/26
 */
@Mapper
public interface DevopsVulCodeDependencyVersionConvert {

    DevopsVulCodeDependencyVersionConvert INSTANCE = Mappers.getMapper(DevopsVulCodeDependencyVersionConvert.class);

    @Mapping(source = "id", target = "depenId")
    DevopsVulCodeDependencyVersion to(DevopsVulCodeDependency dependency);

}
