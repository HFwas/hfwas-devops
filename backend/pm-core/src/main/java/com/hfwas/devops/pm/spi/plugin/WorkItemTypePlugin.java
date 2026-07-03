package com.hfwas.devops.pm.spi.plugin;

import com.hfwas.devops.pm.workitem.entity.PmWorkItem;

import java.util.List;

public interface WorkItemTypePlugin {
    String typeCode();

    void validateOnCreate(PmWorkItem item);

    void validateOnUpdate(PmWorkItem oldItem, PmWorkItem newItem);

    default List<String> allowedTransitions(String fromStatus, String toStatus) {
        return List.of();
    }
}
