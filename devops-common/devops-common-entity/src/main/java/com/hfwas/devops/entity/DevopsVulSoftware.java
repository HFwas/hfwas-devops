package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import us.springett.parsers.cpe.Cpe;
import us.springett.parsers.cpe.exceptions.CpeValidationException;
import us.springett.parsers.cpe.values.Part;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/2/27
 */
@TableName("devops_vul_software")
public class DevopsVulSoftware extends Cpe {
    @TableId("id")
    private Integer id;
    @TableField("cveid")
    private Long cveid;
    private String type;
    @TableField("cpeEntryId")
    private Integer cpeEntryId;
    @TableField("versionEndExcluding")
    private String versionEndExcluding;
    @TableField("versionEndIncluding")
    private String versionEndIncluding;
    @TableField("versionStartExcluding")
    private String versionStartExcluding;
    @TableField("versionStartIncluding")
    private String versionStartIncluding;
    @TableField("vulnerable")
    private Boolean vulnerable;
    @TableField("ecosystem")
    private String ecosystem;

    public DevopsVulSoftware(Part part, String vendor, String product, String version, String update,
                             String edition, String language, String swEdition, String targetSw, String targetHw, String other,
                             String versionEndExcluding, String versionEndIncluding, String versionStartExcluding,
                             String versionStartIncluding, boolean vulnerable) throws CpeValidationException {
        super(part, vendor, product, version, update, edition, language, swEdition, targetSw, targetHw, other);
        this.versionEndExcluding = versionEndExcluding;
        this.versionEndIncluding = versionEndIncluding;
        this.versionStartExcluding = versionStartExcluding;
        this.versionStartIncluding = versionStartIncluding;
        this.vulnerable = vulnerable;
    }

    public String getVersionStartExcluding() {
        return versionStartExcluding;
    }

    public void setVersionStartExcluding(String versionStartExcluding) {
        this.versionStartExcluding = versionStartExcluding;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getCveid() {
        return cveid;
    }

    public void setCveid(Long cveid) {
        this.cveid = cveid;
    }

    public Integer getCpeEntryId() {
        return cpeEntryId;
    }

    public void setCpeEntryId(Integer cpeEntryId) {
        this.cpeEntryId = cpeEntryId;
    }

    public String getVersionEndExcluding() {
        return versionEndExcluding;
    }

    public void setVersionEndExcluding(String versionEndExcluding) {
        this.versionEndExcluding = versionEndExcluding;
    }

    public String getVersionEndIncluding() {
        return versionEndIncluding;
    }

    public void setVersionEndIncluding(String versionEndIncluding) {
        this.versionEndIncluding = versionEndIncluding;
    }

    public String getVersionStartIncluding() {
        return versionStartIncluding;
    }

    public void setVersionStartIncluding(String versionStartIncluding) {
        this.versionStartIncluding = versionStartIncluding;
    }

    public Boolean getVulnerable() {
        return vulnerable;
    }

    public void setVulnerable(Boolean vulnerable) {
        this.vulnerable = vulnerable;
    }

    public String getEcosystem() {
        return ecosystem;
    }

    public void setEcosystem(String ecosystem) {
        this.ecosystem = ecosystem;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
