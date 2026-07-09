package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionVO {
    /** 稳定 ID（UUID） */
    private String id;
    /** 显示名称，如「开始处理」 */
    private String name;
    /** 目标状态编码 */
    private String toStatus;
    private List<TransitionValidatorVO> validators = new ArrayList<>();
    private List<TransitionPostFunctionVO> postFunctions = new ArrayList<>();
}
