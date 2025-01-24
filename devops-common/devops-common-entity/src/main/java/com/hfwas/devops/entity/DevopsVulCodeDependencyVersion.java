package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/15
 */
@Data
@TableName(value = "devops_vul_code_dependency_version")
public class DevopsVulCodeDependencyVersion {
    private Long id;
    private Long depenId;
    private String version;
}
