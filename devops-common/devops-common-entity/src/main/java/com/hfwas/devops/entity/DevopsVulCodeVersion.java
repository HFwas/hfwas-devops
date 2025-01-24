package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/15
 */
@Data
@TableName(value = "devops_vul_code_version")
public class DevopsVulCodeVersion {
    private Long id;
    private Long depenVersionId;
    private Long codeId;
}
