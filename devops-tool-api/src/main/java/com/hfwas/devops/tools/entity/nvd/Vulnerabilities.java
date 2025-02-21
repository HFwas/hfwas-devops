package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Vulnerabilities {

    @JsonProperty("cve")
    private Cve cve;

}
