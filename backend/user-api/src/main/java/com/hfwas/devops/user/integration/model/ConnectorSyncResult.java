package com.hfwas.devops.user.integration.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorSyncResult {
    private boolean success;
    private String message;
    private int fetched;
    private int created;
    private int updated;
    private int skipped;
    private int disabled;
}
