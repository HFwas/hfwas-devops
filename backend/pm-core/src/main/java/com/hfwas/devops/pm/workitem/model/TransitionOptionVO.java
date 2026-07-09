package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

@Data
public class TransitionOptionVO {
    private String id;
    private String name;
    private String toStatus;
    private String toStatusName;
}
