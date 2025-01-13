package com.hfwas.devops.convert;

import com.hfwas.devops.entity.DevopsVulCwe;
import com.hfwas.devops.tools.entity.cwe.CweCsv;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/1/13
 */
@Mapper
public interface DevopsVulCweConvert {

    DevopsVulCweConvert INSTANCE = Mappers.getMapper(DevopsVulCweConvert.class);

    @Mapping(source = "cweId", target = "id")
    DevopsVulCwe to(CweCsv cweCsv);

}
