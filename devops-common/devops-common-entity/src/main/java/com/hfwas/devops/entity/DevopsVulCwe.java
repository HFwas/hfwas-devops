package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/13
 */
@Data
@EqualsAndHashCode(of = {"id","name","description"})
@TableName(value = "devops_vul_cwe")
public class DevopsVulCwe {
    private Long id;
    private String name;
    private String description;
    private String extendedDescription;
    private Integer type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
