package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/11
 */
@Data
@Getter
@Setter
@TableName(value = "devops_vul")
public class DevopsVul {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modified;
    private String published;
    private String cveId;
    private String ghsaId;
    private String details;
    private String serverity;
    private String ref;
    private String ecosystem;
    private String packages;
    private String introduced;
    private String fixed;
    private String cvssV3Score;
    private String cweIds;

    @TableField(exist = false)
    private List<DevopsVulPackage> affecteds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
