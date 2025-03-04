package com.hfwas.devops.thread;

import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.entity.DevopsVulSoftware;
import com.hfwas.devops.mapper.DevopsVulCpeMapper;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.mapper.DevopsVulSoftwareMapper;
import com.hfwas.devops.service.sync.nvd.VulCpeBuilder;
import com.hfwas.devops.tools.api.vulnerability.NvdApi;
import com.hfwas.devops.tools.entity.nvd.*;
import com.hfwas.devops.tools.entity.nvd.file.Node;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.util.validation.metadata.DatabaseException;
import us.springett.parsers.cpe.Cpe;
import us.springett.parsers.cpe.CpeParser;
import us.springett.parsers.cpe.exceptions.CpeParsingException;
import us.springett.parsers.cpe.exceptions.CpeValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.thread
 * @date 2025/2/27
 */
@Slf4j
public class NvdDataRunnable implements Runnable{

    private static final String cpe23Start = "cpe:2.3:a:";

    private DevopsVulMapper vulMapper;
    private DevopsVulCpeMapper cpeMapper;
    private DevopsVulSoftwareMapper softwareMapper;
    private NvdApi nvdApi;
    private String apiKey;
    private Integer index;
    private List<DevopsVul> vuls;

    public NvdDataRunnable(DevopsVulMapper vulMapper, DevopsVulCpeMapper cpeMapper, DevopsVulSoftwareMapper softwareMapper, NvdApi nvdApi, String apiKey, Integer index, List<DevopsVul> vuls) {
        this.vulMapper = vulMapper;
        this.cpeMapper = cpeMapper;
        this.softwareMapper = softwareMapper;
        this.nvdApi = nvdApi;
        this.apiKey = apiKey;
        this.index = index;
        this.vuls = vuls;
    }

    @Override
    public void run() {
        NvdResponse cves = nvdApi.nvd(apiKey, index, 2000);
        log.info("nvdApi.nvd.total: {}", cves.getTotalResults());
        Map<String, DevopsVul> devopsVulMap = vuls.stream().filter(devopsVul -> devopsVul.getCveId() != null).collect(Collectors.toMap(DevopsVul::getCveId, devopsVul -> devopsVul));
        List<Vulnerabilities> vulnerabilities = cves.getVulnerabilities();
        for (Vulnerabilities vulnerability : vulnerabilities) {
            Cve cve = vulnerability.getCve();
            String cveId = cve.getId();
            DevopsVul devopsVul = devopsVulMap.get(cveId);
            if (testCveCpeStartWithFilter(cve) && Objects.nonNull(devopsVul)) {
                List<DevopsVulSoftware> devopsVulSoftwares = parseCpes(cve);
                devopsVulSoftwares = devopsVulSoftwares.stream().map(devopsVulSoftware -> {
                    devopsVulSoftware.setCveid(devopsVul.getId());
                    devopsVulSoftware.setEcosystem(devopsVul.getEcosystem());
                    devopsVulSoftware.setType(devopsVulSoftware.getPart().getAbbreviation());
                    return devopsVulSoftware;
                }).collect(Collectors.toList());
                for (DevopsVulSoftware devopsVulSoftware : devopsVulSoftwares) {
                    try {
                        softwareMapper.insertSoftware(devopsVulSoftware);
                    } catch (Exception e) {
                        log.error("cpeMapper.insert error", e);
                    }
                }
            }
        }
    }

    public List<DevopsVulSoftware> parseCpes(Cve cve) {
        ArrayList<DevopsVulSoftware> devopsVulSoftwares = new ArrayList<>();
        final List<CpeMatch> cpeEntries = cve.getConfigurations().stream()
                .map(Configurations::getNodes)
                .flatMap(List::stream)
                .map(Node::getCpeMatch)
                .flatMap(List::stream)
                .filter(predicate -> predicate.getCriteria() != null)
                .filter(predicate -> predicate.getCriteria().startsWith(cpe23Start))
                //this single CPE entry causes nearly 100% FP - so filtering it at the source.
                .filter(entry -> !("CVE-2009-0754".equals(cve.getId())
                        && "cpe:2.3:a:apache:apache:*:*:*:*:*:*:*:*".equals(entry.getCriteria())))
                .collect(Collectors.toList());
        final VulCpeBuilder vulCpeBuilder = new VulCpeBuilder();
        try {
            cpeEntries.forEach(entry -> {
                Cpe cpe = parseCpe(entry, cve.getId());
                vulCpeBuilder.cpe(parseCpe(entry, cve.getId()))
                        .versionEndExcluding(entry.getVersionEndExcluding())
                        .versionStartExcluding(entry.getVersionStartExcluding())
                        .versionEndIncluding(entry.getVersionEndIncluding())
                        .versionStartIncluding(entry.getVersionStartIncluding())
                        .vulnerable(entry.getVulnerable());

                vulCpeBuilder.part(cpe.getPart()).wfVendor(cpe.getWellFormedVendor()).wfProduct(cpe.getWellFormedProduct())
                        .wfVersion(cpe.getWellFormedVersion()).wfUpdate(cpe.getWellFormedUpdate())
                        .wfEdition(cpe.getWellFormedEdition()).wfLanguage(cpe.getWellFormedLanguage())
                        .wfSwEdition(cpe.getWellFormedSwEdition()).wfTargetSw(cpe.getWellFormedTargetSw())
                        .wfTargetHw(cpe.getWellFormedTargetHw()).wfOther(cpe.getWellFormedOther());
                try {
                    DevopsVulSoftware build = vulCpeBuilder.build();
                    devopsVulSoftwares.add(build);
                } catch (CpeValidationException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            log.error("parseCpe error", e);
        }
        return devopsVulSoftwares;

    }

    boolean testCveCpeStartWithFilter(final Cve cve) {
        if (cve.getConfigurations() != null) {
            //cycle through to see if this is a CPE we care about (use the CPE filters
            return cve.getConfigurations().stream()
                    .map(Configurations::getNodes)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .map(Node::getCpeMatch)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(cpe -> cpe != null && cpe.getCriteria() != null)
                    .anyMatch(cpe -> cpe.getCriteria().startsWith(cpe23Start));
        }
        return false;
    }

    private Cpe parseCpe(CpeMatch cpe, String cveId) throws DatabaseException {
        final Cpe parsedCpe;
        try {
            parsedCpe = CpeParser.parse(cpe.getCriteria(), true);
        } catch (CpeParsingException ex) {
            log.debug("NVD (" + cveId + ") contain an invalid 2.3 CPE: " + cpe.getCriteria());
            throw new DatabaseException("Unable to parse CPE: " + cpe.getCriteria(), ex);
        }
        return parsedCpe;
    }
}
