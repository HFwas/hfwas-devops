package com.hfwas.devops.pm.workitem.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchFieldChangeVO {
    private String fieldKey;
    private Object oldValue;
    private Object newValue;
}
