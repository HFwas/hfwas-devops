package cn.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class Cve {
    private String id;
    private String sourceIdentifier;
    private String published;
    private String lastModified;
    private String vulnStatus;
    private List<Void> cveTags;
    private List<CveDescription> descriptions;
    private CveMetrics metrics;
    private List<Weaknesses> weaknesses;
    private String configurations;
    private  references;
}
