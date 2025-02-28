package com.hfwas.devops.service.sync.nvd;

import com.hfwas.devops.entity.DevopsVulSoftware;
import us.springett.parsers.cpe.Cpe;
import us.springett.parsers.cpe.CpeBuilder;
import us.springett.parsers.cpe.exceptions.CpeValidationException;
import us.springett.parsers.cpe.values.Part;

/**
 * @author houfei
 * @package com.hfwas.devops.service.sync.nvd
 * @date 2025/2/27
 */
public class VulCpeBuilder extends CpeBuilder {

    private String versionEndExcluding = null;

    private String versionEndIncluding = null;

    private String versionStartExcluding = null;

    private String versionStartIncluding = null;

    private boolean vulnerable = true;

    @Override
    public CpeBuilder part(Part part) {
        return super.part(part);
    }

    public VulCpeBuilder cpe(Cpe cpe) {
        this.part(cpe.getPart()).wfVendor(cpe.getWellFormedVendor()).wfProduct(cpe.getWellFormedProduct())
                .wfVersion(cpe.getWellFormedVersion()).wfUpdate(cpe.getWellFormedUpdate())
                .wfEdition(cpe.getWellFormedEdition()).wfLanguage(cpe.getWellFormedLanguage())
                .wfSwEdition(cpe.getWellFormedSwEdition()).wfTargetSw(cpe.getWellFormedTargetSw())
                .wfTargetHw(cpe.getWellFormedTargetHw()).wfOther(cpe.getWellFormedOther());
        return this;
    }

    public VulCpeBuilder versionEndExcluding(String versionEndExcluding) {
        this.versionEndExcluding = versionEndExcluding;
        return this;
    }

    public VulCpeBuilder versionEndIncluding(String versionEndIncluding) {
        this.versionEndIncluding = versionEndIncluding;
        return this;
    }

    public VulCpeBuilder versionStartExcluding(String versionStartExcluding) {
        this.versionStartExcluding = versionStartExcluding;
        return this;
    }

    public VulCpeBuilder versionStartIncluding(String versionStartIncluding) {
        this.versionStartIncluding = versionStartIncluding;
        return this;
    }

    public VulCpeBuilder vulnerable(boolean vulnerable) {
        this.vulnerable = vulnerable;
        return this;
    }

    @Override
    public DevopsVulSoftware build() throws CpeValidationException {
        DevopsVulSoftware devopsVulSoftware = new DevopsVulSoftware(getPart(), getVendor(), getProduct(),
                getVersion(), getUpdate(), getEdition(),
                getLanguage(), getSwEdition(), getTargetSw(), getTargetHw(), getOther(), versionEndExcluding, versionEndIncluding, versionStartExcluding,
                versionStartIncluding, vulnerable);
        reset();
        return devopsVulSoftware;
    }

    /**
     * Resets the Vulnerable Software Builder to a clean state.
     */
    @Override
    protected void reset() {
        super.reset();
        versionEndExcluding = null;
        versionEndIncluding = null;
        versionStartExcluding = null;
        versionStartIncluding = null;
        vulnerable = true;
    }

}
