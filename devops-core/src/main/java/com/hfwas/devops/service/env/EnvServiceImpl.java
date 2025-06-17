package com.hfwas.devops.service.env;

import com.hfwas.devops.convert.DevopsEnvConvert;
import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.entity.DevopsEnv;
import com.hfwas.devops.mapper.DevopsEnvMapper;
import com.hfwas.devops.vo.env.DevopsEnvVO;
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
        DevopsEnv devopsEnv = DevopsEnvConvert.INSTANCE.to(devopsEnvDto);
        this.devopsEnvMapper.insert(devopsEnv);
        return devopsEnv.getId();
    }

    @Override
    public boolean updateEnv(DevopsEnvDto devopsEnvDto) {
        DevopsEnv devopsEnv = DevopsEnvConvert.INSTANCE.to(devopsEnvDto);
        devopsEnvMapper.updateById(devopsEnv);
        return true;
    }

    @Override
    public boolean deleteEnvById(Long id) {
        devopsEnvMapper.deleteById(id);
        return true;
    }

    @Override
    public List<DevopsEnvVO> listEnv(Long projectId) {
        List<DevopsEnv> devopsEnvs = devopsEnvMapper.listEnv(projectId);
        List<DevopsEnvVO> devopsEnvVOS = DevopsEnvConvert.INSTANCE.to(devopsEnvs);
        return devopsEnvVOS;
    }

}
