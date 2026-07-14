package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.BoardQueryDto;
import com.hfwas.devops.pm.api.dto.MetaTypesQueryDto;
import com.hfwas.devops.pm.api.dto.WorkItemTypeDeleteDto;
import com.hfwas.devops.pm.field.DetailTabCatalog;
import com.hfwas.devops.pm.field.FeatureCatalog;
import com.hfwas.devops.pm.field.model.DetailTabDefinition;
import com.hfwas.devops.pm.field.model.FeatureDefinition;
import com.hfwas.devops.pm.meta.PmMetaService;
import com.hfwas.devops.pm.meta.PmWorkItemType;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import com.hfwas.devops.pm.workitem.service.WorkItemService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pm")
@RequiredArgsConstructor
public class PmMetaController {

    private final PmMetaService metaService;
    private final WorkItemService workItemService;
    private final StatusDefinitionService statusDefinitionService;

    @PostMapping("/meta/types")
    public BaseResult<List<PmWorkItemType>> types(@RequestBody(required = false) MetaTypesQueryDto dto) {
        boolean includeDisabled = dto != null && Boolean.TRUE.equals(dto.getIncludeDisabled());
        return BaseResult.ok(metaService.listTypes(includeDisabled));
    }

    @OperLog(module = "pm", action = "save", bizType = "work_item_type", summary = "保存事项类型", bizId = "#result.data")
    @PostMapping("/meta/types/save")
    public BaseResult<Long> saveType(@RequestBody PmWorkItemType type) {
        return BaseResult.ok(metaService.saveType(type));
    }

    @OperLog(module = "pm", action = "delete", bizType = "work_item_type", summary = "删除事项类型")
    @PostMapping("/meta/types/delete")
    public BaseResult<Void> deleteType(@RequestBody WorkItemTypeDeleteDto dto) {
        metaService.deleteType(dto != null ? dto.getCode() : null);
        return BaseResult.ok(null);
    }

    @PostMapping("/meta/detail-tabs")
    public BaseResult<List<DetailTabDefinition>> detailTabs() {
        return BaseResult.ok(DetailTabCatalog.implemented());
    }

    @PostMapping("/meta/features")
    public BaseResult<List<FeatureDefinition>> features() {
        return BaseResult.ok(FeatureCatalog.implemented());
    }

    @PostMapping("/board")
    public BaseResult<Map<String, List<PmWorkItem>>> board(@RequestBody BoardQueryDto dto) {
        Map<String, List<PmWorkItem>> board = new LinkedHashMap<>();
        for (StatusDefinitionVO status : statusDefinitionService.listStatusOptions(dto.getProjectId(), dto.getTypeCode())) {
            board.put(status.getStatusCode(),
                    workItemService.listByStatus(dto.getProjectId(), dto.getTypeCode(), status.getStatusCode()));
        }
        return BaseResult.ok(board);
    }
}
