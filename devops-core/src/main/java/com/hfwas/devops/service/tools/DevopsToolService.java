package com.hfwas.devops.service.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.dto.tools.DevopsToolUpdateDto;
import com.hfwas.devops.entity.DevopsTool;

/**
 * @author houfei
 * @package com.hfwas.devops.service.tools
 * @date 2025/3/16
 */
public interface DevopsToolService extends IService<DevopsTool> {

    boolean insert(DevopsToolDto devopsToolDto);
    boolean edit(DevopsToolUpdateDto devopsToolDto);
    Page<DevopsTool> page(DevopsToolDto devopsToolDto);
    boolean delete(Integer id);

}
