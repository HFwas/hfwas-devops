package com.hfwas.devops.service.env;

import com.hfwas.devops.entity.DevopsEnvInstance;

/**
 * @author houfei
 * @package com.hfwas.devops.service.env
 * @date 2025/6/12
 */
public interface EnvInstanceService {

    boolean saveEnvInstance(DevopsEnvInstance envInstance);

    boolean updateEnvInstance(DevopsEnvInstance devopsEnvInstance);

    boolean deleteEnvInstance(Long id);

    boolean connect(Long id);

}
