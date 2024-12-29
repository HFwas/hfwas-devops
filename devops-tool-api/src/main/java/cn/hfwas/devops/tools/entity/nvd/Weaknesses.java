package cn.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class Weaknesses {
    private String source;
    private String type;
    private List<CveDescription> CveDescription;
}
