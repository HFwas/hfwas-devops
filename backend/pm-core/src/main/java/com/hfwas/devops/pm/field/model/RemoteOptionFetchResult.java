package com.hfwas.devops.pm.field.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteOptionFetchResult {
    private boolean success;
    private String message;
    private List<ResolvedFieldOption> options;
}
