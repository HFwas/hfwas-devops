package com.hfwas.devops.pm.spi.plugin;

import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractWorkItemTypePlugin implements WorkItemTypePlugin {
    @Override
    public void validateOnCreate(PmWorkItem item) {
        if (StringUtils.isBlank(item.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (StringUtils.isBlank(item.getStatus())) {
            item.setStatus("open");
        }
    }

    @Override
    public void validateOnUpdate(PmWorkItem oldItem, PmWorkItem newItem) {
        if (StringUtils.isBlank(newItem.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }
    }
}
