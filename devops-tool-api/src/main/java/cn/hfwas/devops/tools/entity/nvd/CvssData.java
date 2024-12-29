package cn.hfwas.devops.tools.entity.nvd;

import lombok.Data;

@Data
public class CvssData {
    private String version;
    private String vectorString;
    private Integer baseScore;
    private String accessVector;
    private String accessComplexity;
    private String authentication;
    private String confidentialityImpact;
    private String integrityImpact;
    private String availabilityImpact;
}
