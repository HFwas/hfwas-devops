package com.hfwas.devops.convert;

import com.hfwas.devops.dto.repos.DevopsRepoDto;
import com.hfwas.devops.entity.DevopsRepo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author hfwas
 * @package com.hfwas.devops.convert
 * @date 2025/6/18
 */
@Mapper
public interface DevopsRepoConvert {

    DevopsRepoConvert INSTANCE = Mappers.getMapper(DevopsRepoConvert.class);

    DevopsRepo to(DevopsRepoDto devopsRepoDto);

}
