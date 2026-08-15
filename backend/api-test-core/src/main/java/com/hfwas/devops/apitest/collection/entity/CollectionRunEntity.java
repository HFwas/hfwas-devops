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
 * 集合执行记录实体
 *
 * @author hfwas
 */
@Data
@TableName(value = "api_collection_run", autoResultMap = true)
public class CollectionRunEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属集合ID */
    private Long collectionId;

    /** 项目ID */
    private Long projectId;

    /** 执行时使用的环境ID */
    private Long environmentId;

    /** 执行名称 */
    private String name;

    /** 执行状态 RUNNING / COMPLETED / FAILED */
    private String status;

    /** 总项数 */
    private Integer totalCount;

    /** 通过数 */
    private Integer passedCount;

    /** 失败数 */
    private Integer failedCount;

    /** 错误数 */
    private Integer errorCount;

    /** 总耗时（毫秒） */
    private Long durationMs;

    /** 触发方式 MANUAL / SCHEDULED */
    private String triggerMode;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间（仅有创建时间，无更新时间） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}