package com.hfwas.devops.apitest.collection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集合项实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_collection_item", autoResultMap = true)
public class CollectionItemEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属集合ID */
    private Long collectionId;

    /** 所属文件夹ID，null为根级 */
    private Long folderId;

    /** 引用的接口定义ID */
    private Long definitionId;

    /** 覆盖名称（为空则使用接口定义名称） */
    private String name;

    /** 覆盖描述 */
    private String description;

    /** 是否启用 0-禁用 1-启用 */
    private Integer enabled;

    /** 排序序号 */
    private Integer sortOrder;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}