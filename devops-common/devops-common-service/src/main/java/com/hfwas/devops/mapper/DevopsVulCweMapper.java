package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsVulCwe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/1/13
 */
@Mapper
public interface DevopsVulCweMapper extends BaseMapper<DevopsVulCwe> {

    DevopsVulCwe selectById(Long id);

    List<DevopsVulCwe> list(@Param("ids") List<Long> ids);

    List<DevopsVulCwe> listByGhsa(@Param("ids") List<String> ids);

    boolean save(@Param("devopsVulCwe")DevopsVulCwe devopsVulCwe);

    boolean saveBatch(@Param("devopsVulCwes")List<DevopsVulCwe> devopsVulCwes);

}
