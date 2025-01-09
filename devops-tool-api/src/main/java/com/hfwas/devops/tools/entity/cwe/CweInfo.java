package com.hfwas.devops.tools.entity.cwe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.cwe
 * @date 2025/1/9
 */
@Data
public class CweInfo {
    @SerializedName("ID")
    private String id;
    @SerializedName("Type")
    private String type;
}
