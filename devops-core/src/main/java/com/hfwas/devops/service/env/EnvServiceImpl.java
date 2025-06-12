package com.hfwas.devops.service.env;

import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.entity.DevopsEnv;
import com.hfwas.devops.mapper.DevopsEnvMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.env
 * @date 2025/6/12
 */
@Service
public class EnvServiceImpl implements EnvService {

    private final DevopsEnvMapper devopsEnvMapper;

    public EnvServiceImpl(DevopsEnvMapper devopsEnvMapper) {
        this.devopsEnvMapper = devopsEnvMapper;
    }

    @Override
    public Long saveEnv(DevopsEnvDto devopsEnvDto) {
        devopsEnvMapper.saveEnv(devopsEnvDto);
        return devopsEnvDto.getId();
    }

    @Override
    public boolean updateEnv(DevopsEnvDto devopsEnvDto) {
        devopsEnvMapper.updateEnv(devopsEnvDto);
        return false;
    }

    @Override
    public boolean deleteEnvById(Long id) {
        devopsEnvMapper.deleteEnvById(id);
        return false;
    }

    @Override
    public List<DevopsEnv> listEnv(Long projectId) {
        List<DevopsEnv> devopsEnvs = devopsEnvMapper.listEnv(projectId);

        return devopsEnvs;
    }

}
