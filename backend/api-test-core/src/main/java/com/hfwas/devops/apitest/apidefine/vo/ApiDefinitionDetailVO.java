package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口定义详情视图（含参数和响应）
 *
 * @author hfwas
 */
@Data
@Schema(description = "接口定义详情视图")
public class ApiDefinitionDetailVO {

    @Schema(description = "接口ID")
    private Long id;

    @Schema(description = "所属项目ID")
    private Long projectId;

    @Schema(description = "所属分组ID")
    private Long groupId;

    @Schema(description = "分组名称")
    private String groupName;

    @Schema(description = "接口名称")
    private String name;

    @Schema(description = "请求路径")
    private String path;

    @Schema(description = "请求方式")
    private String method;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "当前版本号")
    private String version;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "接口描述")
    private String description;

    @Schema(description = "协议")
    private String protocol;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "请求Content-Type")
    private String contentType;

    @Schema(description = "请求参数列表")
    private List<ApiDefinitionParamVO> params;

    @Schema(description = "响应定义列表")
    private List<ApiDefinitionResponseVO> responses;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}