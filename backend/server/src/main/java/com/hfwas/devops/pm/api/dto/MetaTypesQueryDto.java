package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class MetaTypesQueryDto {
    /** true 时包含已停用类型（管理页用） */
    private Boolean includeDisabled;
}
