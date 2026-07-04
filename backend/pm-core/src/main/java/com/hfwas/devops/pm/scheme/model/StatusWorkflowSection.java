package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Status workflow section within an issue type scheme. */
@Data
public class StatusWorkflowSection {
    private List<ExportedStatusDefinition> statuses = new ArrayList<>();
}
