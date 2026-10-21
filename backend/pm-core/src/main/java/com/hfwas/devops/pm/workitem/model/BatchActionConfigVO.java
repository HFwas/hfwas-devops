package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.List;

@Data
public class BatchActionConfigVO {
    private String actionCode;
    private boolean enabled;
    private int maxItems;
    private boolean requireConfirm;
    private List<String> allowedRoles;
    private List<String> fieldWhitelist;
    private boolean configured;
}
