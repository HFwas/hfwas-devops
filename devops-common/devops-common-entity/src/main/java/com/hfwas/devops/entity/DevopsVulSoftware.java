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
    private Integer cveid;
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
}
