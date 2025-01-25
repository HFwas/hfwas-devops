package com.hfwas.devops.convert;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/1/24
 */
@Mapper
public interface DevopsVulPackageConvert {

    DevopsVulPackageConvert INSTANCE = Mappers.getMapper(DevopsVulPackageConvert.class);

}
