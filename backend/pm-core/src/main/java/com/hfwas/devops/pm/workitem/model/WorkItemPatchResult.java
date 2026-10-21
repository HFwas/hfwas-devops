package com.hfwas.devops.pm.workitem.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemPatchResult {
    private List<BatchFieldChangeVO> changes;
    private Long version;
}
