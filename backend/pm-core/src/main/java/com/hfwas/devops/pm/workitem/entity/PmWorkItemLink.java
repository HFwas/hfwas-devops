package com.hfwas.devops.pm.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_work_item_link")
public class PmWorkItemLink {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sourceId;
    private Long targetId;
    private String linkType;
    private LocalDateTime createTime;
}
