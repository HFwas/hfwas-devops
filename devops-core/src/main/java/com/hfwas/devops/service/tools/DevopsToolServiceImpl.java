package com.hfwas.devops.service.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.convert.DevopsToolConvert;
import com.hfwas.devops.dto.tools.DevopsToolDto;
import com.hfwas.devops.dto.tools.DevopsToolUpdateDto;
import com.hfwas.devops.entity.DevopsTool;
import com.hfwas.devops.mapper.DevopsToolMapper;
import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service.tools
 * @date 2025/3/16
 */
@Service
public class DevopsToolServiceImpl extends ServiceImpl<DevopsToolMapper, DevopsTool> implements DevopsToolService {

    @Override
    public boolean insert(DevopsToolDto devopsToolDto) {
        DevopsTool devopsTool = DevopsToolConvert.INSTANCE.to(devopsToolDto);
        boolean save = save(devopsTool);
        return save;
    }

    @Override
    public boolean edit(DevopsToolUpdateDto devopsToolDto) {
        DevopsTool devopsTool = DevopsToolConvert.INSTANCE.to(devopsToolDto);
        boolean update = this.updateById(devopsTool);
        return update;
    }

    @Override
    public Page<DevopsTool> page(DevopsToolDto devopsToolDto) {
        Page<DevopsTool> devopsToolPage = new Page<>();
        LambdaQueryWrapper<DevopsTool> eq = Wrappers.<DevopsTool>lambdaQuery().eq(DevopsTool::getName, devopsToolDto.getIp());

        Page<DevopsTool> page = page(devopsToolPage, eq);
        return page;
    }

    @Override
    public boolean delete(Integer id) {
        //
        removeById(id);
        return false;
    }

}
