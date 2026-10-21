package com.hfwas.devops.pm.meta;

import lombok.Data;

@Data
public class PmWorkItemTypeTemplateVO {
    private String code;
    private String name;
    private String description;
    private String icon;
    private String color;
    private String workflowTemplateCode;
}
