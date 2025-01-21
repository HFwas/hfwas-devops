package com.hfwas.devops.service.vul;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    @Value("${devops.vulnerability.path}")
    private String vulnerabilityPath;

    @Resource
    DevopsVulMapper devopsVulMapper;

    public void sync() {
        try {
            // 构造命令（通过 shell 执行完整命令）
//            List<String> command = List.of(
//                    "bash", "-c",
//                    "wget -qO - https://github.com/github/advisory-database/archive/refs/heads/main.tar.gz | tar xz -C /Users/houfei/github/ghsa --strip-components=1"
//            );
            List<String> command = Lists.newArrayList();
            Path advisoryDatabasePath = Paths.get(String.format("%s/advisory-database", vulnerabilityPath));
            boolean exists = Files.exists(advisoryDatabasePath);
            if (exists) {
                command = List.of(
                        "bash", "-c",
                        String.format("cd %s/advisory-database && git pull", vulnerabilityPath)
                );
            } else {
                command = List.of(
                        "bash", "-c",
                        String.format("cd %s && git clone -b main https://github.com/github/advisory-database.git", vulnerabilityPath)
                );
            }
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // 合并错误流
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("【sync git advisory-database】:{}", line);
            }
            // 等待进程完成
            int exitCode = process.waitFor();
            log.info("【sync git advisory-database】Exit Code: " + exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Exit Code: " + exitCode);
            }

            long timeMillis = System.currentTimeMillis();
            log.info("【sync】start time:{}", timeMillis);
            Stream<Path> walk = Files.walk(Paths.get(String.format("%s/advisory-database", vulnerabilityPath)));
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
                List<GithubAdvisories> githubAdvisories1 = new ArrayList<>(50);
                paths.stream().parallel().forEach(path -> {
                    try {
                        String readString = Files.readString(path);
                        GithubAdvisories githubAdvisories = gson.fromJson(readString, GithubAdvisories.class);
                        if (Objects.nonNull(githubAdvisories) && !Strings.isNullOrEmpty(githubAdvisories.getGhsaId())) {
                            githubAdvisories1.add(githubAdvisories);
                        }
                    } catch (IOException e) {
                    }
                });

                List<DevopsVul> advisoriesList = githubAdvisories1.stream().parallel()
                        .filter(githubAdvisories -> Objects.nonNull(githubAdvisories))
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
                log.info("【sync】devopsVulMapper.saveBatch: {}", (System.currentTimeMillis() - timeMillis));
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
                    for (DevopsVul devopsVul : updateAdvisoriesList) {
                        devopsVulMapper.update(devopsVul);
                    }
                }
                i++;
                log.info("【sync】{} end time:{}", i, (System.currentTimeMillis() - timeMillis));
            }
        } catch (Exception e) {
            log.error("【sync github add and update GithubAdvisories】,{}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

}
