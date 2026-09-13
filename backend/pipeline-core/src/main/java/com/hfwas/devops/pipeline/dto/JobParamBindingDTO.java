package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class JobParamBindingDTO {
    /** fixed = 写死；runtime = 运行时选择 */
    private String mode;
    /** 写死时的值；GIT_REF 写死时用流水线 gitRef */
    private String value;
}
