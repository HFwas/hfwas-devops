package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class TaskKindPublishDTO {
    /** 发布备注，可空。 */
    private String note;
    /**
     * 显式版本号，形如 1.2.0。省略时由后端按当前版本的补丁位 +1，
     * 尚无版本时生成 1.0.0。保存接口不会改版本。
     */
    private String version;
}
