package cn.hfwas.devops.tools.entity.nexus.role;

import lombok.Data;

@Data
public class NexusRole {
    private String description;

    private String id;

    private String name;

    private String[] privileges;

    private Boolean readOnly;

    private String[] roles;

    private String source;
}
