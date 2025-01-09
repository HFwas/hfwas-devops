package com.hfwas.devops.tools.entity.cwe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.cwe
 * @date 2025/1/9
 */
@Data
public class CweWeakness {

    @SerializedName("Weaknesses")
    private List<CweWeaknes> weaknesses;

}
