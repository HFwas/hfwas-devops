package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/2/27
 */
@TableName("devops_vul_cpe")
public class DevopsVulCpe {
    private Integer id;
    private String part;
    private String vendor;
    private String product;
    private String version;
    private String update_version;
    private String edition;
    private String lang;
    private String sw_edition;
    private String target_sw;
    @TableField("target_hw")
    private String target_hw;
    private String other;
    private String ecosystem;
}
