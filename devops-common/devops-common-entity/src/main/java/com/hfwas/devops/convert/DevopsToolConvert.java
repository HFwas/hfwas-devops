package com.hfwas.devops.convert;

import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.dto.tools.DevopsToolUpdateDto;
import com.hfwas.devops.entity.DevopsTool;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/3/16
 */
@Mapper
public interface DevopsToolConvert {

    DevopsToolConvert INSTANCE = Mappers.getMapper(DevopsToolConvert.class);

    DevopsTool to(DevopsToolDto devopsToolDto);
    DevopsTool to(DevopsToolUpdateDto devopsToolDto);

}
