package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsEnvInstance;
import org.apache.ibatis.annotations.Param;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/6/12
 */
public interface DevopsEnvInstanceMapper extends BaseMapper<DevopsEnvInstance> {

    Long saveEnvInstance(@Param("envInstance")DevopsEnvInstance envInstance);
    Long updateEnvInstance(@Param("envInstance")DevopsEnvInstance envInstance);
    Long deleteEnvInstance(@Param("id")Long id);

}
