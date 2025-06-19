package com.hfwas.devops.service.repos;

import com.hfwas.devops.convert.DevopsRepoConvert;
import com.hfwas.devops.dto.repos.DevopsRepoDto;
import com.hfwas.devops.entity.DevopsRepo;
import com.hfwas.devops.mapper.DevopsRepoMapper;
import org.springframework.stereotype.Service;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.repos
 * @date 2025/6/18
 */
@Service
public class DevopsRepoServiceImpl implements DevopsRepoService {

    private final DevopsRepoMapper devopsRepoMapper;

    public DevopsRepoServiceImpl(DevopsRepoMapper devopsRepoMapper) {
        this.devopsRepoMapper = devopsRepoMapper;
    }

    @Override
    public Long create(DevopsRepoDto devopsRepoDto) {
        DevopsRepo devopsRepo = DevopsRepoConvert.INSTANCE.to(devopsRepoDto);
        devopsRepoMapper.insert(devopsRepo);
        return devopsRepo.getId();
    }

    @Override
    public Boolean delete(Long id) {
        devopsRepoMapper.deleteById(id);
        return true;
    }

    @Override
    public Boolean update(DevopsRepoDto devopsRepoDto) {
        DevopsRepo devopsRepo = DevopsRepoConvert.INSTANCE.to(devopsRepoDto);
        this.devopsRepoMapper.updateById(devopsRepo);
        return true;
    }

    @Override
    public Long page(DevopsRepoDto devopsRepoDto) {
        return 0L;
    }

}
