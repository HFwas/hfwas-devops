package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionPostFunctionPresetVO {
    private String id;
    private String label;
    private String description;
    /** bell | user | webhook | field */
    private String icon;
    private String type;
    private String fieldKey;
    private Object value;
    /** preset | template */
    private String kind;
}
