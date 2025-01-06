package com.hfwas.devops.tools.entity.nvd;

import com.hfwas.devops.tools.enums.EventNameEnums;
import lombok.Data;

@Data
public class CveChange {
    private String cveId;
    private EventNameEnums eventName;
    private String cveChangeId;
    private String sourceIdentifier;
    private String created;
    private String details;
}
