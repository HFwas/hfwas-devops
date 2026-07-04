package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.FieldCatalogListDto;
import com.hfwas.devops.pm.api.dto.FieldDefinitionListDto;
import com.hfwas.devops.pm.api.dto.FieldDefinitionSaveDto;
import com.hfwas.devops.pm.api.dto.FieldTypeBindingDto;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import com.hfwas.devops.pm.field.model.FieldRemoteOptionsConfig;
import com.hfwas.devops.pm.field.model.RemoteOptionFetchResult;
import com.hfwas.devops.pm.field.model.ResolvedFieldOption;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
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

    @PostMapping("/available")
    public BaseResult<List<FieldDefinition>> available(@RequestBody FieldDefinitionListDto dto) {
        return BaseResult.ok(fieldDefinitionService.listAvailableForType(dto.getProjectId(), dto.getTypeCode()));
    }

    @OperLog(module = "pm", action = "save", bizType = "field_definition", summary = "添加字段到事项类型")
    @PostMapping("/add-to-type")
    public BaseResult<Void> addToType(@RequestBody FieldTypeBindingDto dto) {
        fieldDefinitionService.addToType(dto.getProjectId(), dto.getFieldId(), dto.getTypeCode());
        return BaseResult.ok(null);
    }

    @OperLog(module = "pm", action = "delete", bizType = "field_definition", summary = "从事项类型移除字段")
    @PostMapping("/remove-from-type")
    public BaseResult<Void> removeFromType(@RequestBody FieldTypeBindingDto dto) {
        fieldDefinitionService.removeFromType(dto.getProjectId(), dto.getFieldId(), dto.getTypeCode());
        return BaseResult.ok(null);
    }

    @OperLog(module = "pm", action = "save", bizType = "field_definition", summary = "保存字段定义", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody FieldDefinitionSaveDto dto) {
        return BaseResult.ok(fieldDefinitionService.save(dto.getDefinition(), dto.getOptions()));
    }

    @OperLog(module = "pm", action = "delete", bizType = "field_definition", summary = "删除字段定义", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        fieldDefinitionService.deleteField(id);
        return BaseResult.ok(null);
    }

    @GetMapping("/options")
    public BaseResult<List<FieldOption>> options(@RequestParam("fieldId") Long fieldId) {
        return BaseResult.ok(fieldDefinitionService.listOptions(fieldId));
    }

    @GetMapping("/options/resolve")
    public BaseResult<List<ResolvedFieldOption>> resolveOptions(@RequestParam("fieldId") Long fieldId) {
        return BaseResult.ok(fieldDefinitionService.resolveOptions(fieldId));
    }

    @PostMapping("/options/remote/preview")
    public BaseResult<RemoteOptionFetchResult> previewRemoteOptions(@RequestBody FieldRemoteOptionsConfig config) {
        return BaseResult.ok(fieldDefinitionService.previewRemoteOptions(config));
    }

    @GetMapping("/{id}")
    public BaseResult<FieldDefinition> getById(@PathVariable Long id) {
        return BaseResult.ok(fieldDefinitionService.getById(id));
    }
}
