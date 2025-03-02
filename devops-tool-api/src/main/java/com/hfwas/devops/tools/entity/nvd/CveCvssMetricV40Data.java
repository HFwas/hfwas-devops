package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/3/2
 */
@Data
public class CveCvssMetricV40Data {
    @JsonProperty("version")
    private String version;
    @JsonProperty("vectorString")
    private String vectorString;
    @JsonProperty("attackVector")
    private String attackVector;
    @JsonProperty("attackComplexity")
    private String attackComplexity;
    @JsonProperty("attackRequirements")
    private String attackRequirements;
    @JsonProperty("privilegesRequired")
    private String privilegesRequired;
    @JsonProperty("userInteraction")
    private String userInteraction;
    @JsonProperty("vulnerableSystemConfidentiality")
    private String vulnConfidentialityImpact;
    @JsonProperty("vulnerableSystemIntegrity")
    private String vulnIntegrityImpact;
    @JsonProperty("vulnerableSystemAvailability")
    private String vulnAvailabilityImpact;
    @JsonProperty("subsequentSystemConfidentiality")
    private String subConfidentialityImpact;
    @JsonProperty("subsequentSystemIntegrity")
    private String subIntegrityImpact;
    @JsonProperty("subsequentSystemAvailability")
    private String subAvailabilityImpact;
    @JsonProperty("exploitMaturity")
    private String exploitMaturity;
    @JsonProperty("confidentialityRequirements")
    private String confidentialityRequirement;
    @JsonProperty("integrityRequirements")
    private String integrityRequirement;
    @JsonProperty("availabilityRequirements")
    private String availabilityRequirement;
    @JsonProperty("modifiedAttackVector")
    private String modifiedAttackVector;
    @JsonProperty("modifiedAttackComplexity")
    private String modifiedAttackComplexity;
    @JsonProperty("modifiedAttackRequirements")
    private String modifiedAttackRequirements;
    @JsonProperty("modifiedPrivilegesRequired")
    private String modifiedPrivilegesRequired;
    @JsonProperty("modifiedUserInteraction")
    private String modifiedUserInteraction;
    @JsonProperty("modifiedVulnerableSystemConfidentiality")
    private String modifiedVulnConfidentialityImpact;
    @JsonProperty("modifiedVulnerableSystemIntegrity")
    private String modifiedVulnIntegrityImpact;
    @JsonProperty("modifiedVulnerableSystemAvailability")
    private String modifiedVulnAvailabilityImpact;
    @JsonProperty("modifiedSubsequentSystemConfidentiality")
    private String modifiedSubConfidentialityImpact;
    @JsonProperty("modifiedSubsequentSystemIntegrity")
    private String modifiedSubIntegrityImpact;
    @JsonProperty("modifiedSubsequentSystemAvailability")
    private String modifiedSubAvailabilityImpact;
    @JsonProperty("safety")
    private String safety;
    @JsonProperty("automatable")
    private String automatable;
    @JsonProperty("recovery")
    private String recovery;
    @JsonProperty("valueDensity")
    private String valueDensity;
    @JsonProperty("vulnerabilityResponseEffort")
    private String vulnerabilityResponseEffort;
    @JsonProperty("providerUrgency")
    private String providerUrgency;
    @JsonProperty("baseScore")
    private Double baseScore;
    @JsonProperty("baseSeverity")
    private String baseSeverity;
    @JsonProperty("threatScore")
    private Double threatScore;
    @JsonProperty("threatSeverity")
    private String threatSeverity;
    @JsonProperty("environmentalScore")
    private Double environmentalScore;
    @JsonProperty("environmentalSeverity")
    private String environmentalSeverity;
}
