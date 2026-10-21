package com.hfwas.devops.pm.workitem.io;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.module.mapper.PmProjectModuleMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import com.hfwas.devops.user.spi.UserIdentityResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class WorkItemIoValueCodecTest {

    @Mock
    private FieldDefinitionService fieldDefinitionService;
    @Mock
    private StatusDefinitionService statusDefinitionService;
    @Mock
    private PmProjectModuleMapper moduleMapper;
    @Mock
    private UserIdentityResolver userIdentityResolver;

    @InjectMocks
    private WorkItemIoValueCodec codec;

    @Test
    void roundTripsEndTime() {
        PmWorkItem item = new PmWorkItem();
        item.setEndTime(LocalDateTime.of(2026, 11, 1, 18, 0, 0));

        Object exported = codec.exportValue(item, endTimeColumn(), null);
        Object imported = codec.importValue("2026-11-01", endTimeColumn(), null, 10L, "task");

        assertThat(exported).isEqualTo("2026-11-01 18:00:00");
        assertThat(imported).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    void rejectsInvalidEndTime() {
        assertThatThrownBy(() -> codec.importValue("not-a-date", endTimeColumn(), null, 10L, "task"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.DATE_FORMAT_INVALID.getCode());
    }

    private WorkItemIoColumn endTimeColumn() {
        WorkItemIoColumn column = new WorkItemIoColumn();
        column.setFieldKey("end_time");
        column.setFieldName("截止时间");
        column.setFieldType("DATETIME");
        column.setSystemField(true);
        return column;
    }
}
