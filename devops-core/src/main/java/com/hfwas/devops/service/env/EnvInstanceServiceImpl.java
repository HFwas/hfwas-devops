package com.hfwas.devops.service.env;

import com.hfwas.devops.convert.DevopsEnvInstanceConvert;
import com.hfwas.devops.dto.env.DevopsEnvInstanceDto;
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
    public boolean saveEnvInstance(DevopsEnvInstanceDto devopsEnvInstanceDto) {
        DevopsEnvInstance devopsEnvInstance = DevopsEnvInstanceConvert.INSTANCE.to(devopsEnvInstanceDto);
        devopsEnvInstanceMapper.insert(devopsEnvInstance);
        return true;
    }

    @Override
    public boolean updateEnvInstance(DevopsEnvInstanceDto devopsEnvInstanceDto) {
        DevopsEnvInstance devopsEnvInstance = DevopsEnvInstanceConvert.INSTANCE.to(devopsEnvInstanceDto);
        devopsEnvInstanceMapper.updateById(devopsEnvInstance);
        return true;
    }

    @Override
    public boolean deleteEnvInstance(Long id) {
        devopsEnvInstanceMapper.deleteById(id);
        return true;
    }

    @Override
    public boolean connect(Long id) {
        return true;
    }

}
