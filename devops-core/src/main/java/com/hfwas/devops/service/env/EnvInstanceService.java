package com.hfwas.devops.service.env;

import com.hfwas.devops.dto.env.DevopsEnvInstanceDto;

/**
 * @author houfei
 * @package com.hfwas.devops.service.env
 * @date 2025/6/12
 */
public interface EnvInstanceService {

    boolean saveEnvInstance(DevopsEnvInstanceDto envInstance);

    boolean updateEnvInstance(DevopsEnvInstanceDto devopsEnvInstance);

    boolean deleteEnvInstance(Long id);

    boolean connect(Long id);

}
