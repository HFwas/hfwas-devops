package com.hfwas.devops.user.integration.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorTestResult {
    private boolean success;
    private String message;
    /** Sample user count when test includes search. */
    private Integer sampleCount;
}
