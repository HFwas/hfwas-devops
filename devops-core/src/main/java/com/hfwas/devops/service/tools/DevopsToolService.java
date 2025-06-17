package com.hfwas.devops.service.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.dto.tools.DevopsToolUpdateDto;
import com.hfwas.devops.entity.DevopsTool;

/**
 * @author houfei
 * @package com.hfwas.devops.service.tools
 * @date 2025/3/16
 */
public interface DevopsToolService {

    /**
     * 新增 devops 工具
     * @param devopsToolDto
     * @return
     */
    Integer insert(DevopsToolDto devopsToolDto);

    /**
     * 编辑 devops 工具
     * @param devopsToolDto
     * @return
     */
    boolean edit(DevopsToolUpdateDto devopsToolDto);

    /**
     * 分页查询 devops 工具
     * @param devopsToolDto
     * @return
     */
    Page<DevopsTool> page(DevopsToolDto devopsToolDto);

    /**
     * 逻辑删除 devops 工具
     * @param id
     * @return
     */
    boolean delete(Integer id);

}
