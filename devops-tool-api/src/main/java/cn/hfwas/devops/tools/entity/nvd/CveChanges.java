package cn.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class CveChanges {
    private List<CveChange> CveChanges;
}
