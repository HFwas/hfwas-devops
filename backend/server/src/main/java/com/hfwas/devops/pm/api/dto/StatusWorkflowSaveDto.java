package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import lombok.Data;

import java.util.List;

@Data
public class StatusWorkflowSaveDto {
    private Long projectId;
    private String typeCode;
    private List<StatusDefinitionVO> statuses;
}
