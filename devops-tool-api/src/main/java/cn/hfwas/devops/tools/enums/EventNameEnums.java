package cn.hfwas.devops.tools.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum EventNameEnums {
    Received("CVE Received"),
    IniAnalysis("Initial Analysis"),
    Reanalysis("Reanalysis"),
    Modified("CVE Modified"),
    ModiAnalysis("Modified Analysis"),
    Translated("CVE Translated"),
    Comment("Vendor Comment"),
    Update("CVE Source Update"),
    CpeDepRemap("CPE Deprecation Remap"),
    Remap("CWE Remap"),
    RefTagUpdate("Reference Tag Update"),
    Rejected("CVE Rejected"),
    Unrejected("CVE Unrejected"),
    CveCisaKevUpdate("CVE CISA KEV Update"),
    ;

    private String name;
}
