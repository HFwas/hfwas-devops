package com.hfwas.devops.apitest.apidefine.dto;

import com.hfwas.devops.common.page.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 接口定义查询条件
 *
 * @author hfwas
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "接口定义查询条件")
public class ApiDefinitionQueryDTO extends PageRequest {

    @Schema(description = "所属项目ID")
    private Long projectId;

    @Schema(description = "所属分组ID")
    private Long groupId;

    @Schema(description = "接口名称（模糊搜索）")
    private String keyword;

    @Schema(description = "请求方式")
    private String method;

    @Schema(description = "状态 DRAFT/PUBLISHED/DEPRECATED")
    private String status;

    @Schema(description = "标签列表（交集查询）")
    private List<String> tags;
}