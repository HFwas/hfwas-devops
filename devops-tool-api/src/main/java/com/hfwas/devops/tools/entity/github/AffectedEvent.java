package com.hfwas.devops.tools.entity.github;

import lombok.Data;

@Data
public class AffectedEvent {
    private String introduced;
    private String fixed;
}
