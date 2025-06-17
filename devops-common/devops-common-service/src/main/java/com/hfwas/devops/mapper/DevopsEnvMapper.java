package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.dto.env.DevopsEnvDto;
import com.hfwas.devops.entity.DevopsEnv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.mapper
 * @date 2025/3/16
 */
@Mapper
public interface DevopsEnvMapper extends BaseMapper<DevopsEnv> {

    boolean saveEnv(@Param("devopsEnv") DevopsEnvDto devopsEnvDto);

    boolean updateEnv(@Param("devopsEnv") DevopsEnvDto devopsEnvDto);

    boolean deleteEnvById(@Param("id") Long id);

    List<DevopsEnv> listEnv(@Param("projectId") Long projectId);

}
