package com.hfwas.devops.tools.entity.cwe;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.cwe
 * @date 2025/1/8
 */
@Data
public class CweVersion {
    private String ContentVersion;
    private String ContentDate;
    private String TotalWeaknesses;
    private String TotalCategories;
    private String TotalViews;
}
