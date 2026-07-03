package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.FieldDefinitionListDto;
import com.hfwas.devops.pm.api.dto.FieldLayoutSaveDto;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.field.service.FieldLayoutService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pm/fields/layout")
@RequiredArgsConstructor
public class PmFieldLayoutController {

    private final FieldLayoutService fieldLayoutService;
    private final FieldDefinitionService fieldDefinitionService;

    @PostMapping("/get")
    public BaseResult<TypeFieldLayoutConfig> get(@RequestBody FieldDefinitionListDto dto) {
        var fields = fieldDefinitionService.listRawByProjectAndType(dto.getProjectId(), dto.getTypeCode());
        TypeFieldLayoutConfig layout = fieldLayoutService.resolveLayout(dto.getProjectId(), dto.getTypeCode(), fields);
        return BaseResult.ok(layout);
    }

    @OperLog(module = "pm", action = "save", bizType = "field_layout", summary = "保存字段布局", bizId = "#dto.projectId")
    @PostMapping("/save")
    public BaseResult<Void> save(@RequestBody FieldLayoutSaveDto dto) {
        fieldLayoutService.saveLayout(dto.getProjectId(), dto.getTypeCode(), dto.getLayout());
        return BaseResult.ok(null);
    }
}
