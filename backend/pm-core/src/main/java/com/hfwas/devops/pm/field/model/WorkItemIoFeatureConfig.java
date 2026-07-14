package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkItemIoFeatureConfig {
    /** 缺省视为 true（列表保留导入导出入口） */
    private Boolean enabled;
    private List<String> exportFieldKeys = new ArrayList<>();
    private List<String> importFieldKeys = new ArrayList<>();
}
