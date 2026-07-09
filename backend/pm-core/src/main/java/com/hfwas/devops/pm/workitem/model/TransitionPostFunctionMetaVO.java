package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionPostFunctionMetaVO {
    private List<TransitionPostFunctionPresetVO> presets = new ArrayList<>();
    private List<TransitionFieldMetaVO> fields = new ArrayList<>();
    private List<String> placeholders = List.of("{title}", "{itemKey}", "{fromStatus}", "{toStatus}");
}
