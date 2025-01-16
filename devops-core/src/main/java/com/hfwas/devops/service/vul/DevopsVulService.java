package com.hfwas.devops.service.vul;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hfwas.devops.convert.DevopsVulConvert;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.tools.entity.cwe.CvssSeverity;
import com.hfwas.devops.tools.entity.github.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul
 * @date 2025/1/16
 */
@Slf4j
@Service
public class DevopsVulService {

    @Resource
    DevopsVulMapper devopsVulMapper;

    public void sync() {
        try {
            long timeMillis = System.currentTimeMillis();
            log.info("【sync】start time:{}", timeMillis);
            Stream<Path> walk = Files.walk(Paths.get("/Users/houfei/github/ghsa/advisories/github-reviewed/2024/12"));
            List<Path> collect = walk.parallel()
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("GHSA") || path.getFileName().toString().startsWith("CVE"))
                    .filter(path -> path.getFileName().toString().endsWith("json"))
                    .sorted()
                    .collect(Collectors.toList());
            Gson gson = new Gson();

            List<List<Path>> collectPartition = Lists.partition(collect, 50);
            int i = 0;
            List<DevopsVul> devopsVulList = devopsVulMapper.list(null);
            Map<String, String> devopsVulListMap = devopsVulList.stream().parallel().collect(Collectors.toMap(DevopsVul::getGhsaId, DevopsVul::getModified));
            Set<String> ghsaIds = devopsVulListMap.keySet();
            for (List<Path> paths : collectPartition) {
                log.info("【sync】collectPartition: {}", i);
                ArrayList<GithubAdvisories> githubAdvisories1 = new ArrayList<>();
                paths.stream().parallel().forEach(path -> {
                    try {
                        String readString = Files.readString(path);
                        GithubAdvisories githubAdvisories = gson.fromJson(readString, GithubAdvisories.class);
                        githubAdvisories1.add(githubAdvisories);
                    } catch (IOException e) {
                    }
                });
                log.info("【sync】devopsVulMapper.list: {}", (System.currentTimeMillis() - timeMillis));

                List<DevopsVul> advisoriesList = githubAdvisories1.stream().parallel()
                        .filter(githubAdvisories -> {
                            String ghsaId = githubAdvisories.getGhsaId();
                            if (CollectionUtils.isEmpty(devopsVulList)) {
                                return true;
                            } else {
                                return CollectionUtils.isNotEmpty(devopsVulList) && !ghsaIds.contains(githubAdvisories.getGhsaId());
                            }
                        })
                        .map(githubAdvisories -> {
                            DevopsVul convert = DevopsVulConvert.INSTANCE.convert(githubAdvisories);
                            convert.setGhsaId(githubAdvisories.getGhsaId());
                            if (CollectionUtils.isNotEmpty(githubAdvisories.getAliases())){
                                convert.setCveId(githubAdvisories.getAliases().get(0));
                            }
                            List<CvssSeverity> serverity = githubAdvisories.getServerity();
                            if (CollectionUtils.isNotEmpty(serverity)){
                                Optional<CvssSeverity> cvssV3 = serverity.stream().filter(server -> server.getType().equals("CVSS_V3")).findFirst();
                                if (cvssV3.isPresent()){
                                    CvssSeverity cvssSeverity = cvssV3.get();
                                    convert.setCvssV3Score(cvssSeverity.getScore());
                                }
                            }
                            DatabaseSpecific databaseSpecific = githubAdvisories.getDatabaseSpecific();
                            if (CollectionUtils.isNotEmpty(databaseSpecific.getCweIds())) {
                                String cweIds = Joiner.on(",").join(databaseSpecific.getCweIds());
                                convert.setCweIds(cweIds);
                            }
                            convert.setServerity(databaseSpecific.getSeverity());
                            convert.setReferencess(gson.toJson(githubAdvisories.getReferences()));
                            if (CollectionUtils.isNotEmpty(githubAdvisories.getAffected())){
                                GithubAffected githubAffected = githubAdvisories.getAffected().get(0);
                                JsonObject githubAffectedPackages = githubAffected.getPackages();
                                String ecosystem = gson.toJson(githubAffectedPackages.get("ecosystem")).replace("\"", "");
                                convert.setEcosystem(ecosystem);
                                String packages = gson.toJson(githubAffectedPackages.get("name")).replace("\"", "");
                                convert.setPackages(packages);

                                List<Range> ranges = githubAffected.getRanges();
                                if (CollectionUtils.isNotEmpty(ranges)) {
                                    Range range = ranges.get(0);
                                    List<AffectedEvent> events = range.getEvents();
                                    convert.setIntroduced(gson.toJson(events.get(0)));
                                    if (events.size() > 1) {
                                        convert.setFixed(gson.toJson(events.get(1)));
                                    }
                                }
                            }
                            return convert;
                        })
                        .collect(Collectors.toList());

                if (CollectionUtils.isNotEmpty(advisoriesList)) {
                    devopsVulMapper.saveBatch(advisoriesList);
                }
                log.info("【sync】devopsVulMapper.list: {}", (System.currentTimeMillis() - timeMillis));
                List<DevopsVul> updateAdvisoriesList = githubAdvisories1.stream()
                        .parallel()
                        .filter(githubAdvisories -> CollectionUtils.isNotEmpty(devopsVulList) && ghsaIds.contains(githubAdvisories.getGhsaId()) && !devopsVulListMap.get(githubAdvisories.getGhsaId()).equals(githubAdvisories.getModified()))
                        .map(githubAdvisories -> {
                            DevopsVul convert = DevopsVulConvert.INSTANCE.convert(githubAdvisories);
                            convert.setGhsaId(githubAdvisories.getGhsaId());
                            if (CollectionUtils.isNotEmpty(githubAdvisories.getAliases())){
                                convert.setCveId(githubAdvisories.getAliases().get(0));
                            }
                            List<CvssSeverity> serverity = githubAdvisories.getServerity();
                            if (CollectionUtils.isNotEmpty(serverity)){
                                Optional<CvssSeverity> cvssV3 = serverity.stream().filter(server -> server.getType().equals("CVSS_V3")).findFirst();
                                if (cvssV3.isPresent()){
                                    CvssSeverity cvssSeverity = cvssV3.get();
                                    convert.setCvssV3Score(cvssSeverity.getScore());
                                }
                            }
                            DatabaseSpecific databaseSpecific = githubAdvisories.getDatabaseSpecific();
                            if (CollectionUtils.isNotEmpty(databaseSpecific.getCweIds())) {
                                String cweIds = Joiner.on(",").join(databaseSpecific.getCweIds());
                                convert.setCweIds(cweIds);
                            }
                            convert.setServerity(gson.toJson(githubAdvisories.getServerity()));
                            convert.setReferencess(gson.toJson(githubAdvisories.getReferences()));
                            if (CollectionUtils.isNotEmpty(githubAdvisories.getAffected())){
                                GithubAffected githubAffected = githubAdvisories.getAffected().get(0);
                                JsonObject githubAffectedPackages = githubAffected.getPackages();
                                String ecosystem = gson.toJson(githubAffectedPackages.get("ecosystem")).replace("\"", "");
                                convert.setEcosystem(ecosystem);
                                String packages = gson.toJson(githubAffectedPackages.get("name")).replace("\"", "");
                                convert.setPackages(packages);

                                List<Range> ranges = githubAffected.getRanges();
                                if (CollectionUtils.isNotEmpty(ranges)) {
                                    Range range = ranges.get(0);
                                    List<AffectedEvent> events = range.getEvents();
                                    convert.setIntroduced(gson.toJson(events.get(0)));
                                    if (events.size() > 1) {
                                        convert.setFixed(gson.toJson(events.get(1)));
                                    }
                                }
                            }
                            return convert;
                        })
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(updateAdvisoriesList)) {
                    devopsVulMapper.updateBatch(updateAdvisoriesList);
                }
                i++;
                log.info("【sync】end time:{}", (System.currentTimeMillis() - timeMillis));
            }
        } catch (Exception e) {
            log.error("【sync github add and update GithubAdvisories】,{}", e.getMessage());
        }

    }

}
