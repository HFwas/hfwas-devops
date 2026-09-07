package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.CredentialSaveDTO;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.service.PipelineCredentialService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pipeline/credentials")
public class PipelineCredentialController {

    private final PipelineCredentialService credentialService;

    public PipelineCredentialController(PipelineCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public BaseResult<List<CredentialVO>> list() {
        return BaseResult.ok(credentialService.list());
    }

    @GetMapping("/{id}")
    public BaseResult<CredentialVO> get(@PathVariable("id") Long id) {
        return BaseResult.ok(credentialService.get(id));
    }

    @PostMapping
    public BaseResult<Long> save(@RequestBody CredentialSaveDTO dto) {
        return BaseResult.ok(credentialService.save(dto));
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable("id") Long id) {
        credentialService.delete(id);
        return BaseResult.ok(null);
    }
}
