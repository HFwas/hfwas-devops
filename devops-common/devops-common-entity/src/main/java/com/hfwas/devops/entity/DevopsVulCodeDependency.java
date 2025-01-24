package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/1/13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "devops_vul_code_dependency")
public class DevopsVulCodeDependency {
    private Long id;
    private String company;
    private String dependencyName;
    private String version;
    private Integer type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
