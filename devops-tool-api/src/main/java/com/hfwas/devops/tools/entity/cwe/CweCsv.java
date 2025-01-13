package com.hfwas.devops.tools.entity.cwe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.cwe
 * @date 2025/1/9
 */
@Data
public class CweCsv {
    @SerializedName("CWE-ID")
    private Long cweId;
    @SerializedName("Name")
    private String name;
    @SerializedName("Weakness Abstraction")
    private String weaknessAbstraction;
    @SerializedName("Status")
    private String status;
    @SerializedName("Description")
    private String description;
    @SerializedName("Extended Description")
    private String extendedDescription;
    @SerializedName("Related Weaknesses")
    private String relatedWeaknesses;
    @SerializedName("Weakness Ordinalities")
    private String weaknessOrdinalities;
    @SerializedName("Applicable Platforms")
    private String applicablePlatforms;
    @SerializedName("Background Details")
    private String backgroundDetails;
    @SerializedName("Alternate Terms")
    private String alternateTerms;
    @SerializedName("Modes Of Introduction")
    private String modesOfIntroduction;
    @SerializedName("Exploitation Factors")
    private String exploitationFactors;
    @SerializedName("Likelihood of Exploit")
    private String likelihoodExploit;
    @SerializedName("Common Consequences")
    private String commonConsequences;
    @SerializedName("Detection Methods")
    private String detectionMethods;
    @SerializedName("Potential Mitigations")
    private String potentialMitigations;
    @SerializedName("Observed Examples")
    private String observedExamples;
    @SerializedName("Functional Areas")
    private String functionalAreas;
    @SerializedName("Affected Resources")
    private String affectedResources;
    @SerializedName("Taxonomy Mappings")
    private String mappings;
    @SerializedName("Related Attack Patterns")
    private String patterns;
    @SerializedName("Notes")
    private String notes;

}
