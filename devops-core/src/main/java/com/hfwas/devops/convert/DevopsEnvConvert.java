package com.hfwas.devops.convert;

import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.entity.DevopsEnv;
import com.hfwas.devops.vo.env.DevopsEnvVO;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.convert
 * @date 2025/6/16
 */
public interface DevopsEnvConvert {

    DevopsEnvConvert INSTANCE = Mappers.getMapper(DevopsEnvConvert.class);

    DevopsEnv to(DevopsEnvDto devopsEnvDto);
    DevopsEnvVO to(DevopsEnv devopsEnv);
    List<DevopsEnvVO> to(List<DevopsEnv> devopsEnvs);

}
