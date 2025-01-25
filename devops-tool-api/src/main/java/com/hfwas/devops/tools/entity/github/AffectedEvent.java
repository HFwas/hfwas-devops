package com.hfwas.devops.tools.entity.github;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class AffectedEvent {
    private String introduced;
    @SerializedName("last_affected")
    private String lastAffected;
    private String fixed;
}
