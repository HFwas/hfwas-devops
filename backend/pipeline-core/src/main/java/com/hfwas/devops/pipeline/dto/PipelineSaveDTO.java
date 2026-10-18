package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class PipelineSaveDTO {
    private Long id;
    private String name;
    /** 仓库、分支、凭证由 CLONE 任务的参数绑定提供（REPO_URL / GIT_REF / CREDENTIAL_ID）。 */
    private List<PipelineStageDTO> stages;
}
