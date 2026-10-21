package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.project.service.ProjectAccessGuard;
import com.hfwas.devops.pm.workitem.entity.PmBatchActionConfig;
import com.hfwas.devops.pm.workitem.mapper.PmBatchActionConfigMapper;
import com.hfwas.devops.pm.workitem.model.BatchActionConfigVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchActionConfigService {

    public static final String ACTION_UPDATE_FIELDS = "UPDATE_FIELDS";
    public static final String ACTION_TRANSITION = "TRANSITION";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_EXPORT = "EXPORT";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_COPY = "COPY";

    private static final List<String> ACTION_ORDER = List.of(
            ACTION_UPDATE_FIELDS, ACTION_TRANSITION, ACTION_IMPORT,
            ACTION_EXPORT, ACTION_DELETE, ACTION_COPY);

    private static final List<String> DEFAULT_UPDATE_FIELDS =
            List.of("priority", "assignee_id", "module_id", "end_time");

    private static final Set<String> VALID_ROLES = Set.of("admin", "user");

    private final PmBatchActionConfigMapper configMapper;
    private final ProjectAccessGuard projectAccessGuard;
    private final FieldDefinitionService fieldDefinitionService;
    private final CurrentUserAccessor currentUserAccessor;

    public List<BatchActionConfigVO> list(Long projectId) {
        projectAccessGuard.assertAccess(projectId);
        Map<String, PmBatchActionConfig> saved = configMapper.selectList(
                        Wrappers.<PmBatchActionConfig>lambdaQuery()
                                .eq(PmBatchActionConfig::getProjectId, projectId))
                .stream()
                .collect(Collectors.toMap(PmBatchActionConfig::getActionCode, c -> c, (a, b) -> a));
        List<BatchActionConfigVO> result = new ArrayList<>();
        for (String action : ACTION_ORDER) {
            PmBatchActionConfig row = saved.get(action);
            result.add(row == null ? defaultConfig(action) : toVO(row));
        }
        return result;
    }

    @Transactional
    public BatchActionConfigVO save(Long projectId, BatchActionConfigVO input) {
        projectAccessGuard.assertAccess(projectId);
        requireAdmin();
        if (input == null || input.getActionCode() == null || !ACTION_ORDER.contains(input.getActionCode())) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的批量动作: "
                    + (input == null ? null : input.getActionCode()));
        }
        int maxLimit = maxLimitOf(input.getActionCode());
        if (input.getMaxItems() < 1 || input.getMaxItems() > maxLimit) {
            throw new BizException(ResultCode.BATCH_SIZE_EXCEEDED,
                    "单次上限须在 1–" + maxLimit + " 之间");
        }
        List<String> roles = normalizeRoles(input.getAllowedRoles());
        List<String> whitelist = normalizeWhitelist(projectId, input.getFieldWhitelist(), input.getActionCode());

        PmBatchActionConfig existing = configMapper.selectOne(
                Wrappers.<PmBatchActionConfig>lambdaQuery()
                        .eq(PmBatchActionConfig::getProjectId, projectId)
                        .eq(PmBatchActionConfig::getActionCode, input.getActionCode()));
        PmBatchActionConfig row = existing != null ? existing : new PmBatchActionConfig();
        row.setProjectId(projectId);
        row.setActionCode(input.getActionCode());
        row.setEnabled(input.isEnabled() ? 1 : 0);
        row.setMaxItems(input.getMaxItems());
        row.setRequireConfirm(input.isRequireConfirm() ? 1 : 0);
        row.setAllowedRoles(roles);
        row.setFieldWhitelist(whitelist);
        if (existing == null) {
            configMapper.insert(row);
        } else {
            row.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(row);
            if (row.getFieldWhitelist() == null) {
                // MP 默认忽略 null，需显式清空非批量编辑动作的旧白名单。
                configMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PmBatchActionConfig>()
                        .eq("id", row.getId())
                        .set("field_whitelist", null));
            }
        }
        return toVO(row);
    }

    /**
     * 批量执行前的统一门禁：动作启用、条数上限、角色。
     */
    public BatchActionConfigVO assertActionAllowed(Long projectId, String actionCode, int count) {
        BatchActionConfigVO config = effective(projectId, actionCode);
        if (!config.isEnabled()) {
            throw new BizException(ResultCode.BATCH_ACTION_DISABLED);
        }
        if (count > config.getMaxItems()) {
            throw new BizException(ResultCode.BATCH_SIZE_EXCEEDED,
                    "单次最多 " + config.getMaxItems() + " 条");
        }
        List<String> roles = config.getAllowedRoles();
        if (roles != null && !roles.isEmpty()) {
            String role = currentUserAccessor.current().map(UserContext::getRole)
                    .orElse("").toLowerCase();
            if (!roles.contains(role)) {
                throw new BizException(ResultCode.FORBIDDEN, "当前角色不允许执行该批量操作");
            }
        }
        return config;
    }

    public boolean isFieldAllowed(BatchActionConfigVO config, String fieldKey) {
        List<String> whitelist = config.getFieldWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.contains(fieldKey);
    }

    public BatchActionConfigVO effective(Long projectId, String actionCode) {
        PmBatchActionConfig row = configMapper.selectOne(
                Wrappers.<PmBatchActionConfig>lambdaQuery()
                        .eq(PmBatchActionConfig::getProjectId, projectId)
                        .eq(PmBatchActionConfig::getActionCode, actionCode));
        return row == null ? defaultConfig(actionCode) : toVO(row);
    }

    private BatchActionConfigVO defaultConfig(String actionCode) {
        BatchActionConfigVO vo = new BatchActionConfigVO();
        vo.setActionCode(actionCode);
        vo.setEnabled(!ACTION_DELETE.equals(actionCode));
        vo.setMaxItems(ACTION_IMPORT.equals(actionCode) || ACTION_EXPORT.equals(actionCode) ? 5000 : 100);
        vo.setRequireConfirm(ACTION_DELETE.equals(actionCode) || ACTION_TRANSITION.equals(actionCode));
        vo.setAllowedRoles(new ArrayList<>(List.of("admin", "user")));
        vo.setFieldWhitelist(ACTION_UPDATE_FIELDS.equals(actionCode)
                ? new ArrayList<>(DEFAULT_UPDATE_FIELDS) : null);
        vo.setConfigured(false);
        return vo;
    }

    private BatchActionConfigVO toVO(PmBatchActionConfig row) {
        BatchActionConfigVO vo = new BatchActionConfigVO();
        vo.setActionCode(row.getActionCode());
        vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        vo.setMaxItems(row.getMaxItems() != null ? row.getMaxItems() : 100);
        vo.setRequireConfirm(row.getRequireConfirm() != null && row.getRequireConfirm() == 1);
        vo.setAllowedRoles(row.getAllowedRoles() != null ? row.getAllowedRoles() : List.of());
        vo.setFieldWhitelist(row.getFieldWhitelist());
        vo.setConfigured(true);
        return vo;
    }

    private List<String> normalizeRoles(List<String> roles) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                String normalized = role.trim().toLowerCase();
                if (!VALID_ROLES.contains(normalized)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "不支持的角色: " + role);
                }
                unique.put(normalized, normalized);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<String> normalizeWhitelist(Long projectId, List<String> whitelist, String actionCode) {
        if (!ACTION_UPDATE_FIELDS.equals(actionCode)) {
            return null;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return new ArrayList<>(DEFAULT_UPDATE_FIELDS);
        }
        Map<String, FieldDefinition> definitions = fieldDefinitionService.listCatalogByProject(projectId)
                .stream()
                .collect(Collectors.toMap(FieldDefinition::getFieldKey, d -> d, (a, b) -> a));
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : whitelist) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String key = raw.trim();
            FieldDefinition def = definitions.get(key);
            if (def == null || def.getBatchEditable() == null || def.getBatchEditable() != 1) {
                throw new BizException(ResultCode.BATCH_FIELD_NOT_ALLOWED,
                        "字段不允许批量编辑: " + key);
            }
            result.put(key, key);
        }
        return new ArrayList<>(result.values());
    }

    private int maxLimitOf(String actionCode) {
        return ACTION_IMPORT.equals(actionCode) || ACTION_EXPORT.equals(actionCode) ? 5000 : 1000;
    }

    private void requireAdmin() {
        if (!currentUserAccessor.isAdmin()) {
            throw new BizException(ResultCode.ADMIN_REQUIRED);
        }
    }
}
