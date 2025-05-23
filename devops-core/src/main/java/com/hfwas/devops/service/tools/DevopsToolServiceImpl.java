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
import org.apache.commons.lang3.StringUtils;
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
        this.baseMapper.saveDevopsTool(devopsTool);
        return true;
    }

    @Override
    public boolean edit(DevopsToolUpdateDto devopsToolDto) {
        DevopsTool devopsTool = DevopsToolConvert.INSTANCE.to(devopsToolDto);
        this.baseMapper.updateDevopsToolById(devopsTool);
        return true;
    }

    @Override
    public Page<DevopsTool> page(DevopsToolDto devopsToolDto) {
        Page<DevopsTool> devopsToolPage = new Page<>(1,100);
        LambdaQueryWrapper<DevopsTool> eq = Wrappers.<DevopsTool>lambdaQuery()
                .eq(StringUtils.isNoneBlank(devopsToolDto.getName()), DevopsTool::getName, devopsToolDto.getIp());

        Page<DevopsTool> page = page(devopsToolPage, eq);
        return page;
    }

    @Override
    public boolean delete(Integer id) {
        //
        this.baseMapper.removeDevopsToolById(id);
        return false;
    }

}
