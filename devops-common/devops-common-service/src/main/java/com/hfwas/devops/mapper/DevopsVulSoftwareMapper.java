package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsVulSoftware;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/2/27
 */
@Mapper
public interface DevopsVulSoftwareMapper extends BaseMapper<DevopsVulSoftware> {

    void insertSoftware(DevopsVulSoftware devopsVulSoftware);

}
