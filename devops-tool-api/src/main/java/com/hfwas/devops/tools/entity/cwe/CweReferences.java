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
public class CweReferences {
    @SerializedName("ExternalReferenceID")
    private String externalReferenceID;
    @SerializedName("Authors")
    private List<String> authors;
    @SerializedName("Title")
    private String title;
    @SerializedName("PublicationYear")
    private String publicationYear;
    @SerializedName("PublicationMonth")
    private String publicationMonth;
    @SerializedName("PublicationDay")
    private String publicationDay;
    @SerializedName("Publisher")
    private String publisher;
    @SerializedName("URL")
    private String url;
    @SerializedName("URLDate")
    private String urlDate;
}
