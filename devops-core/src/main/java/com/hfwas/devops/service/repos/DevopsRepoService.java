package com.hfwas.devops.service.repos;

import com.hfwas.devops.dto.repos.DevopsRepoDto;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.repos
 * @date 2025/6/18
 */
public interface DevopsRepoService {

    Long create(DevopsRepoDto devopsRepoDto);
    Boolean delete(Long id);
    Boolean update(DevopsRepoDto devopsRepoDto);
    Long page(DevopsRepoDto devopsRepoDto);

}
