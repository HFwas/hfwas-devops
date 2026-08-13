package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分组树形视图
 *
 * @author hfwas
 */
@Data
@Schema(description = "分组视图")
public class ApiGroupVO {

    @Schema(description = "分组ID")
    private Long id;

    @Schema(description = "所属项目ID")
    private Long projectId;

    @Schema(description = "父分组ID")
    private Long parentId;

    @Schema(description = "分组名称")
    private String name;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "分组描述")
    private String description;

    @Schema(description = "子分组列表")
    private List<ApiGroupVO> children;

    @Schema(description = "分组下接口数量")
    private Integer apiCount;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}