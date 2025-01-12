package com.hfwas.devops.convert;

import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.tools.entity.github.GithubAdvisories;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author hfwas
 * @package com.hfwas.devops.convert
 * @date 2025/1/12
 */
@Mapper
public interface DevopsVulConvert {

    DevopsVulConvert INSTANCE = Mappers.getMapper(DevopsVulConvert.class);

    @Mapping(ignore = true, source = "severity", target = "severity")
    @Mapping(ignore = true, source = "affected", target = "affected")
    @Mapping(ignore = true, source = "references", target = "references")
    @Mapping(ignore = true, source = "databaseSpecific", target = "databaseSpecific")
    DevopsVul convert(GithubAdvisories githubAdvisories);

}
