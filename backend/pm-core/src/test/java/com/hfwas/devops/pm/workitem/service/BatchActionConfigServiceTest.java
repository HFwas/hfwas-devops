package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.project.service.ProjectAccessGuard;
import com.hfwas.devops.pm.workitem.entity.PmBatchActionConfig;
import com.hfwas.devops.pm.workitem.mapper.PmBatchActionConfigMapper;
import com.hfwas.devops.pm.workitem.model.BatchActionConfigVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchActionConfigServiceTest {

    @Mock
    private PmBatchActionConfigMapper configMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private FieldDefinitionService fieldDefinitionService;
    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private BatchActionConfigService service;

    @BeforeEach
    void setUp() {
        service = new BatchActionConfigService(configMapper, projectAccessGuard,
                fieldDefinitionService, currentUserAccessor);
    }

    @Test
    void defaultsIncludeDisabledDelete() {
        when(configMapper.selectList(any())).thenReturn(List.of());

        List<BatchActionConfigVO> configs = service.list(10L);

        BatchActionConfigVO update = configs.stream()
                .filter(c -> BatchActionConfigService.ACTION_UPDATE_FIELDS.equals(c.getActionCode()))
                .findFirst().orElseThrow();
        assertThat(update.isEnabled()).isTrue();
        assertThat(update.getMaxItems()).isEqualTo(100);
        assertThat(update.isRequireConfirm()).isFalse();
        assertThat(update.getFieldWhitelist()).contains("priority", "end_time");

        BatchActionConfigVO delete = configs.stream()
                .filter(c -> BatchActionConfigService.ACTION_DELETE.equals(c.getActionCode()))
                .findFirst().orElseThrow();
        assertThat(delete.isEnabled()).isFalse();
        assertThat(delete.isRequireConfirm()).isTrue();
        assertThat(configs).extracting(BatchActionConfigVO::isConfigured).containsOnly(false);
    }

    @Test
    void rejectsNonAdminSave() {
        when(currentUserAccessor.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.save(10L, input("UPDATE_FIELDS", 100, List.of("priority"))))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.ADMIN_REQUIRED.getCode());
        verify(configMapper, never()).insert(any(PmBatchActionConfig.class));
    }

    @Test
    void validatesMaxItems() {
        when(currentUserAccessor.isAdmin()).thenReturn(true);

        assertThatThrownBy(() -> service.save(10L, input("UPDATE_FIELDS", 1001, List.of("priority"))))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_SIZE_EXCEEDED.getCode());
    }

    @Test
    void validatesFieldWhitelist() {
        when(currentUserAccessor.isAdmin()).thenReturn(true);
        FieldDefinition risky = new FieldDefinition();
        risky.setFieldKey("risk");
        risky.setFieldName("风险");
        risky.setFieldType("TEXT");
        risky.setSystemFlag(0);
        risky.setBatchEditable(0);
        when(fieldDefinitionService.listCatalogByProject(10L)).thenReturn(List.of(risky));

        assertThatThrownBy(() -> service.save(10L, input("UPDATE_FIELDS", 100, List.of("risk"))))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_FIELD_NOT_ALLOWED.getCode());
    }

    @Test
    void savesAllowedFieldWhitelist() {
        when(currentUserAccessor.isAdmin()).thenReturn(true);
        FieldDefinition priority = new FieldDefinition();
        priority.setFieldKey("priority");
        priority.setFieldName("优先级");
        priority.setFieldType("PRIORITY");
        priority.setSystemFlag(1);
        priority.setBatchEditable(1);
        when(fieldDefinitionService.listCatalogByProject(10L)).thenReturn(List.of(priority));
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.insert(any(PmBatchActionConfig.class))).thenReturn(1);

        BatchActionConfigVO saved = service.save(10L, input("UPDATE_FIELDS", 100, List.of("priority")));

        assertThat(saved.isConfigured()).isTrue();
        assertThat(saved.getFieldWhitelist()).containsExactly("priority");
        verify(configMapper).insert(any(PmBatchActionConfig.class));
    }

    @Test
    void assertActionAllowedRejectsDisabledAndOverLimit() {
        PmBatchActionConfig disabled = row("DELETE", 0, 100);
        when(configMapper.selectOne(any())).thenReturn(disabled);
        assertThatThrownBy(() -> service.assertActionAllowed(10L, "DELETE", 1))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_ACTION_DISABLED.getCode());

        PmBatchActionConfig limited = row("UPDATE_FIELDS", 1, 2);
        when(configMapper.selectOne(any())).thenReturn(limited);
        assertThatThrownBy(() -> service.assertActionAllowed(10L, "UPDATE_FIELDS", 3))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.BATCH_SIZE_EXCEEDED.getCode());
    }

    private BatchActionConfigVO input(String action, int maxItems, List<String> whitelist) {
        BatchActionConfigVO input = new BatchActionConfigVO();
        input.setActionCode(action);
        input.setEnabled(true);
        input.setMaxItems(maxItems);
        input.setRequireConfirm(true);
        input.setAllowedRoles(List.of("admin", "user"));
        input.setFieldWhitelist(whitelist);
        return input;
    }

    private PmBatchActionConfig row(String action, int enabled, int maxItems) {
        PmBatchActionConfig row = new PmBatchActionConfig();
        row.setId(1L);
        row.setProjectId(10L);
        row.setActionCode(action);
        row.setEnabled(enabled);
        row.setMaxItems(maxItems);
        row.setRequireConfirm(1);
        row.setAllowedRoles(List.of("admin", "user"));
        return row;
    }
}
