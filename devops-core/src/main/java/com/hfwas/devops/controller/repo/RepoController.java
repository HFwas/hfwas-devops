package com.hfwas.devops.controller.repo;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.dto.repos.DevopsRepoDto;
import com.hfwas.devops.service.repos.DevopsRepoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author hfwas
 * @package com.hfwas.devops.controller.repo
 * @date 2025/6/18
 */
@RestController
@RequestMapping("/repos")
public class RepoController {

    private final DevopsRepoService devopsRepoService;

    public RepoController(DevopsRepoService devopsRepoService) {
        this.devopsRepoService = devopsRepoService;
    }

    @PostMapping("/create")
    public BaseResult<Long> create(@RequestBody DevopsRepoDto devopsRepoDto) {
        return BaseResult.ok();
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestBody  Long id){
        return BaseResult.ok();
    }

    @PostMapping("/update")
    public BaseResult<Long> update(@RequestBody DevopsRepoDto devopsRepoDto) {
        return BaseResult.ok();
    }

    @PostMapping("/page")
    public BaseResult page(@RequestBody DevopsRepoDto devopsRepoDto) {
        return BaseResult.ok();
    }

}
