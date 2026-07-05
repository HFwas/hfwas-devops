package com.hfwas.devops.pm.project.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ProjectAccessContextVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    private String projectName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;
}
