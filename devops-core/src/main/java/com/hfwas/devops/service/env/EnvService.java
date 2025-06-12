package com.hfwas.devops.service.env;

import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.vo.env.DevopsEnvVO;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.env
 * @date 2025/6/12
 */
public interface EnvService {

    /**
     * 添加环境
     * @param devopsEnvDto
     * @return
     */
    Long saveEnv(DevopsEnvDto devopsEnvDto);

    /**
     * 更新环境
     * @param devopsEnvDto
     * @return
     */
    boolean updateEnv(DevopsEnvDto devopsEnvDto);

    /**
     * 根据ID删除环境
     * @param id
     * @return
     */
    boolean deleteEnvById(Long id);

    /**
     * 获取环境下列表
     * @param projectId
     * @return
     */
    List<DevopsEnvVO> listEnv(Long projectId);

}
