package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.RegistrySaveDTO;
import com.hfwas.devops.container.dto.RegistryUpdateDTO;
import com.hfwas.devops.container.dto.RegistryVO;
import com.hfwas.devops.container.service.registry.RegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/container/registries")
@RequiredArgsConstructor
public class RegistryController {

    private final RegistryService registryService;

    @PostMapping("/page")
    public BaseResult<IPage<RegistryVO>> page(@RequestBody Map<String, Object> params) {
        int pageNo = params.get("pageNo") != null ? Integer.parseInt(params.get("pageNo").toString()) : 1;
        int pageSize = params.get("pageSize") != null ? Integer.parseInt(params.get("pageSize").toString()) : 20;
        return BaseResult.ok(registryService.page(pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public BaseResult<RegistryVO> getById(@PathVariable Long id) {
        return BaseResult.ok(registryService.getById(id));
    }

    @PostMapping
    public BaseResult<Long> create(@Valid @RequestBody RegistrySaveDTO dto) {
        return BaseResult.ok(registryService.create(dto));
    }

    @PutMapping("/{id}")
    public BaseResult<Void> update(@PathVariable Long id, @RequestBody RegistryUpdateDTO dto) {
        registryService.update(id, dto);
        return BaseResult.ok();
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        registryService.delete(id);
        return BaseResult.ok();
    }

    @PostMapping("/{id}/test")
    public BaseResult<Boolean> testConnection(@PathVariable Long id) {
        return BaseResult.ok(registryService.testConnection(id));
    }
}