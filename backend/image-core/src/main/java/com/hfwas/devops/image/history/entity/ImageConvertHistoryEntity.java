package com.hfwas.devops.image.history.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_convert_history")
public class ImageConvertHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sessionId;

    private Long userId;

    private Long tenantId;

    private String fileName;

    private String sourceMime;

    private String targetFormat;

    private String resultFileName;

    private Long resultSize;

    private Integer width;

    private Integer height;

    private Integer strippedGps;

    private String status;

    private String errorMessage;

    @TableLogic
    private Integer deleted;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
