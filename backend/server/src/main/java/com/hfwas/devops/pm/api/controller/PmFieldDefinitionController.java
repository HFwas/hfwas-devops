package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.FieldCatalogListDto;
import com.hfwas.devops.pm.api.dto.FieldDefinitionListDto;
import com.hfwas.devops.pm.api.dto.FieldDefinitionSaveDto;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/fields/definitions")
@RequiredArgsConstructor
public class PmFieldDefinitionController {

    private final FieldDefinitionService fieldDefinitionService;

    @PostMapping("/list")
    public BaseResult<List<FieldDefinition>> list(@RequestBody FieldDefinitionListDto dto) {
        return BaseResult.ok(fieldDefinitionService.listByProjectAndType(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/catalog")
    public BaseResult<List<FieldDefinition>> catalog(@RequestBody FieldCatalogListDto dto) {
        return BaseResult.ok(fieldDefinitionService.listCatalogByProject(dto.getProjectId()));
    }

    @GetMapping("/{id}")
    public BaseResult<FieldDefinition> getById(@PathVariable Long id) {
        return BaseResult.ok(fieldDefinitionService.getById(id));
    }

    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody FieldDefinitionSaveDto dto) {
        return BaseResult.ok(fieldDefinitionService.save(dto.getDefinition(), dto.getOptions()));
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        fieldDefinitionService.deleteField(id);
        return BaseResult.ok(null);
    }

    @GetMapping("/options")
    public BaseResult<List<FieldOption>> options(@RequestParam("fieldId") Long fieldId) {
        return BaseResult.ok(fieldDefinitionService.listOptions(fieldId));
    }
}
