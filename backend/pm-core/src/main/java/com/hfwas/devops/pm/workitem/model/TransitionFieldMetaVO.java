package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionFieldMetaVO {
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private Integer systemFlag;
    private List<TransitionFieldOptionVO> options = new ArrayList<>();
}
