package com.hfwas.devops.pm.field.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TypeFeaturesConfig {
    @JsonProperty("work_item_io")
    private WorkItemIoFeatureConfig workItemIo;
}
