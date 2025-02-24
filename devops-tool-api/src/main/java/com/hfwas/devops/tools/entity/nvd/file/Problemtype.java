package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.CveDescription;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
public class Problemtype {
    @JsonProperty("problemtype_data")
    private List<CveDescription> problemtypeData;
}
