package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.BatchActionConfigVO;
import com.hfwas.devops.pm.workitem.model.BatchOperationItemVO;
import com.hfwas.devops.pm.workitem.model.BatchOperationResultVO;
import com.hfwas.devops.pm.workitem.model.TransitionOptionVO;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.pm.workitem.model.WorkItemPatchResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkItemBatchService {

    private final PmWorkItemMapper workItemMapper;
    private final ProjectService projectService;
    private final WorkItemFieldUpdateService fieldUpdateService;
    private final WorkItemService workItemService;
    private final StatusDefinitionService statusDefinitionService;
    private final BatchActionConfigService batchActionConfigService;

    public BatchOperationResultVO batchUpdate(List<Long> rawIds, Map<String, Object> patch,
                                              Map<String, Long> versions, boolean dryRun) {
        List<Long> ids = normalizeIds(rawIds);
        List<PmWorkItem> items = loadItems(ids);
        Long projectId = assertSameProject(items);
        BatchActionConfigVO config = batchActionConfigService.assertActionAllowed(
                projectId, BatchActionConfigService.ACTION_UPDATE_FIELDS, ids.size());
        if (patch == null || patch.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少提供一个待更新字段");
        }
        if (patch.containsKey("status")) {
            throw new BizException(ResultCode.BAD_REQUEST, "状态变更请使用批量流转接口");
        }

        String batchId = UUID.randomUUID().toString();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setBatchId(batchId);
        result.setDryRun(dryRun);

        for (PmWorkItem item : items) {
            BatchOperationItemVO row = baseItem(item);
            try {
                List<String> blocked = patch.keySet().stream()
                        .filter(key -> !batchActionConfigService.isFieldAllowed(config, key))
                        .toList();
                if (!blocked.isEmpty()) {
                    throw new BizException(ResultCode.BATCH_FIELD_NOT_ALLOWED,
                            "字段不允许批量编辑: " + String.join(", ", blocked));
                }
                Long expectedVersion = versionOf(versions, item.getId());
                WorkItemPatchResult patchResult = dryRun
                        ? fieldUpdateService.previewPatch(item.getId(), patch, expectedVersion)
                        : fieldUpdateService.applyPatch(item.getId(), patch, expectedVersion, batchId);
                row.setOutcome(BatchOperationItemVO.SUCCESS);
                row.setVersion(patchResult.getVersion());
                row.setChanges(patchResult.getChanges());
            } catch (BizException e) {
                applyFailure(row, e.getCode(), e.getMessage());
            } catch (IllegalArgumentException e) {
                applyFailure(row, ResultCode.BAD_REQUEST.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                applyFailure(row, ResultCode.BAD_REQUEST.getCode(), e.getMessage());
            }
            result.addItem(row);
        }
        return result;
    }

    public BatchOperationResultVO batchTransition(List<Long> rawIds, String transitionId, String toStatus,
                                                  Map<String, Object> fields, Map<String, Long> versions,
                                                  boolean dryRun) {
        List<Long> ids = normalizeIds(rawIds);
        List<PmWorkItem> items = loadItems(ids);
        Long projectId = assertSameProject(items);
        batchActionConfigService.assertActionAllowed(
                projectId, BatchActionConfigService.ACTION_TRANSITION, ids.size());
        if (StringUtils.isBlank(transitionId) && StringUtils.isBlank(toStatus)) {
            throw new BizException(ResultCode.STATUS_REQUIRED);
        }

        String batchId = UUID.randomUUID().toString();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setBatchId(batchId);
        result.setDryRun(dryRun);

        for (PmWorkItem item : items) {
            BatchOperationItemVO row = baseItem(item);
            row.setFromStatus(item.getStatus());
            try {
                String resolved = resolveTransition(item, transitionId, toStatus);
                Long expectedVersion = versionOf(versions, item.getId());
                TransitionVO transition = dryRun
                        ? workItemService.previewTransition(item.getId(), resolved, fields, expectedVersion)
                        : workItemService.transitionWithVersion(item.getId(), resolved, fields, expectedVersion);
                row.setOutcome(BatchOperationItemVO.SUCCESS);
                row.setToStatus(transition.getToStatus());
                row.setVersion(WorkItemService.nextVersion(item.getVersion()));
            } catch (BizException e) {
                applyFailure(row, e.getCode(), e.getMessage());
            } catch (IllegalArgumentException e) {
                applyFailure(row, ResultCode.BAD_REQUEST.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                applyFailure(row, ResultCode.BAD_REQUEST.getCode(), e.getMessage());
            }
            result.addItem(row);
        }
        return result;
    }

    private String resolveTransition(PmWorkItem item, String transitionId, String toStatus) {
        if (StringUtils.isNotBlank(transitionId)) {
            TransitionVO transition = statusDefinitionService.findTransition(
                    item.getProjectId(), item.getTypeCode(), item.getStatus(), transitionId.trim());
            if (StringUtils.isNotBlank(toStatus) && !toStatus.trim().equals(transition.getToStatus())) {
                throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                        "流转目标与目标状态不一致");
            }
            return transitionId.trim();
        }
        String target = toStatus.trim();
        AllowedTransitionsVO allowed = statusDefinitionService.allowedTransitions(
                item.getProjectId(), item.getTypeCode(), item.getStatus(), item.getId());
        List<TransitionOptionVO> candidates = allowed.getTransitions() == null ? List.of()
                : allowed.getTransitions().stream()
                .filter(t -> target.equals(t.getToStatus()))
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                    "当前状态不允许流转到 " + target);
        }
        if (candidates.size() > 1) {
            String ids = candidates.stream().map(TransitionOptionVO::getId).collect(Collectors.joining(", "));
            throw new BizException(ResultCode.STATUS_TRANSITION_FORBIDDEN,
                    "存在多个可达流转，请指定 transitionId: " + ids);
        }
        return candidates.get(0).getId();
    }

    private void applyFailure(BatchOperationItemVO row, int code, String message) {
        boolean skipped = code == ResultCode.STATUS_TRANSITION_FORBIDDEN.getCode()
                || code == ResultCode.BATCH_FIELD_NOT_ALLOWED.getCode();
        row.setOutcome(skipped ? BatchOperationItemVO.SKIPPED : BatchOperationItemVO.FAILED);
        row.setCode(code);
        row.setMessage(message);
    }

    private BatchOperationItemVO baseItem(PmWorkItem item) {
        BatchOperationItemVO row = new BatchOperationItemVO();
        row.setId(item.getId());
        return row;
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        return new ArrayList<>(unique);
    }

    private List<PmWorkItem> loadItems(List<Long> ids) {
        List<PmWorkItem> items = workItemMapper.selectBatchIds(ids);
        if (items.size() != ids.size()) {
            throw new BizException(ResultCode.WORK_ITEM_NOT_FOUND);
        }
        return items;
    }

    private Long assertSameProject(List<PmWorkItem> items) {
        Long projectId = items.get(0).getProjectId();
        boolean same = items.stream().allMatch(item -> projectId != null && projectId.equals(item.getProjectId()));
        if (!same) {
            throw new BizException(ResultCode.BATCH_PROJECT_MISMATCH);
        }
        projectService.assertProjectAccess(projectId);
        return projectId;
    }

    private Long versionOf(Map<String, Long> versions, Long id) {
        if (versions == null) {
            return null;
        }
        return versions.get(String.valueOf(id));
    }
}
