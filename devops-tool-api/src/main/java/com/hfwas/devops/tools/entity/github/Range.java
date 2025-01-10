package com.hfwas.devops.tools.entity.github;

import lombok.Data;

import java.util.List;

@Data
public class Range {
    private String type;
    private List<AffectedEvent> events;
}
