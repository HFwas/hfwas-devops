package com.hfwas.devops.service.env;

import com.hfwas.devops.entity.DevopsEnvInstance;
import com.hfwas.devops.mapper.DevopsEnvInstanceMapper;
import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service.env
 * @date 2025/6/12
 */
@Service
public class EnvInstanceServiceImpl implements EnvInstanceService {

    private final DevopsEnvInstanceMapper devopsEnvInstanceMapper;

    public EnvInstanceServiceImpl(DevopsEnvInstanceMapper devopsEnvInstanceMapper) {
        this.devopsEnvInstanceMapper = devopsEnvInstanceMapper;
    }

    @Override
    public boolean saveEnvInstance(DevopsEnvInstance devopsEnvInstance) {
        devopsEnvInstanceMapper.saveEnvInstance(devopsEnvInstance);
        return false;
    }

    @Override
    public boolean updateEnvInstance(DevopsEnvInstance devopsEnvInstance) {
        devopsEnvInstanceMapper.updateEnvInstance(devopsEnvInstance);
        return false;
    }

    @Override
    public boolean deleteEnvInstance(Long id) {
        devopsEnvInstanceMapper.deleteEnvInstance(id);
        return false;
    }

    @Override
    public boolean connect(Long id) {
        return false;
    }

}
