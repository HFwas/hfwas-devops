package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.Map;

@Data
public class FieldRemoteOptionsConfig {
    private String url;
    private String method = "GET";
    private Map<String, String> headers;
    private String body;
    /** JSON 数组所在路径，如 data 或 result.items，空表示根节点为数组 */
    private String dataPath;
    private String valueField = "value";
    private String labelField = "label";
    private Integer cacheSeconds = 300;
}
