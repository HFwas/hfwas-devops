package com.hfwas.devops.convert;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/6/12
 */
@Mapper
public interface DevopsEnvConvert {

    DevopsEnvConvert INSTANCE = Mappers.getMapper(DevopsEnvConvert.class);


}
