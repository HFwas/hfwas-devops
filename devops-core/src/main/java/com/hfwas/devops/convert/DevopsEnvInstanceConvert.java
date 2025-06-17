package com.hfwas.devops.convert;

import com.hfwas.devops.dto.env.DevopsEnvInstanceDto;
import com.hfwas.devops.entity.DevopsEnvInstance;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/6/17
 */
@Mapper
public interface DevopsEnvInstanceConvert {

    DevopsEnvInstanceConvert INSTANCE = Mappers.getMapper(DevopsEnvInstanceConvert.class);

    DevopsEnvInstance to(DevopsEnvInstanceDto devopsEnvInstanceDto);

    List<DevopsEnvInstance> to(List<DevopsEnvInstanceDto> devopsEnvInstanceDtos);

}
