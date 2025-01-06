package com.hfwas.devops.tools.entity.harbor;

import com.google.gson.JsonObject;
import lombok.Data;

/**
 * @author houfei
 * @package cn.hfwas.devops.tools.entity.harbor
 * @date 2025/1/2
 */
@Data
public class HarborProject {
    private String project_name;
    private String count_limit;
    private String registry_id;
    private String storage_limit;
    private JsonObject metadata;
}
