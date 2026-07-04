package com.hfwas.devops.user.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotifyTestResult {
    private boolean success;
    private String message;
}
