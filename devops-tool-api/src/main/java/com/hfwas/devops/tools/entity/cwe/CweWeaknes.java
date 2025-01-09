package com.hfwas.devops.tools.entity.cwe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.cwe
 * @date 2025/1/9
 */
@Data
public class CweWeaknes {
    @SerializedName("ID")
    private String id;
    @SerializedName("Name")
    private String name;
    @SerializedName("Abstraction")
    private String abstraction;
    @SerializedName("Structure")
    private String structure;
    @SerializedName("Status")
    private String status;
    @SerializedName("Diagram")
    private String diagram;
    @SerializedName("Description")
    private String description;
    @SerializedName("ExtendedDescription")
    private String extendedDescription;
    @SerializedName("LikelihoodOfExploit")
    private String likelihoodOfExploit;
    @SerializedName("RelatedWeaknesses")
    private JsonArray relatedWeaknesses;
    @SerializedName("ApplicablePlatforms")
    private JsonArray applicablePlatforms;
    @SerializedName("AlternateTerms")
    private JsonArray alternateTerms;
    @SerializedName("ModesOfIntroduction")
    private JsonArray modesOfIntroduction;
    @SerializedName("CommonConsequences")
    private JsonArray commonConsequences;
    @SerializedName("DetectionMethods")
    private JsonArray detectionMethodsame;
    @SerializedName("PotentialMitigations")
    private JsonArray potentialMitigations;
    @SerializedName("DemonstrativeExamples")
    private JsonArray demonstrativeExamples;
    @SerializedName("FunctionalAreas")
    private JsonArray functionalAreas;
    @SerializedName("AffectedResources")
    private JsonArray affectedResources;
    @SerializedName("TaxonomyMappings")
    private JsonArray taxonomyMappings;
    @SerializedName("RelatedAttackPatterns")
    private JsonArray relatedAttackPatterns;
    @SerializedName("References")
    private List<CweReferences> references;
    @SerializedName("MappingNotes")
    private JsonObject mappingNotes;
    @SerializedName("Notes")
    private JsonArray notes;
    @SerializedName("ContentHistory")
    private JsonArray contentHistory;
}
