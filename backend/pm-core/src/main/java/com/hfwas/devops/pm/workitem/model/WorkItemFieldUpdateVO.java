package com.hfwas.devops.pm.workitem.model;

import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import lombok.Data;

@Data
public class WorkItemFieldUpdateVO {
    private PmWorkItem item;
    private String fieldKey;
    private Object oldValue;
    private Object newValue;
    private String oldLabel;
    private String newLabel;
}
