package com.hfwas.devops.service.vul;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hfwas.devops.convert.DevopsVulConvert;
import com.hfwas.devops.dto.vul.VulDto;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.entity.DevopsVulPackage;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.mapper.DevopsVulPackageMapper;
import com.hfwas.devops.tools.entity.cwe.CvssSeverity;
import com.hfwas.devops.tools.entity.github.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Resource
    DevopsVulPackageMapper devopsVulPackageMapper;

    public IPage<DevopsVul> page(VulDto vulDto) {
        IPage<DevopsVul> vulPage = new Page<>(vulDto.getPageNo(),  vulDto.getPageSize());
        LambdaQueryWrapper<DevopsVul> queryWrapper = Wrappers.<DevopsVul>lambdaQuery()
                .eq(!Strings.isNullOrEmpty(vulDto.getCveId()), DevopsVul::getCveId, vulDto.getCveId())
                .orderByDesc(DevopsVul::getPublished);
        IPage<DevopsVul> devopsVulPage = devopsVulMapper.selectPage(vulPage, queryWrapper);
        return devopsVulPage;
    }

    public DevopsVul getById(Long id) {
        DevopsVul devopsVul = devopsVulMapper.selectById(id);
        return devopsVul;
    }

    @Transactional
    public void sync() {
        try {
            int exitCode = getExitCode();
            log.info("【sync git advisory-database】Exit Code: " + exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Exit Code: " + exitCode);
            }

            long timeMillis = System.currentTimeMillis();
            log.info("【sync】start time:{}", timeMillis);
            Stream<Path> walk = Files.walk(Paths.get(String.format("%s/advisory-database/advisories", vulnerabilityPath)));
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
                        log.error(e.getMessage());
                        throw new RuntimeException(e.getMessage());
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
                            DevopsVul convert = getDevopsVul(githubAdvisories, gson);
                            return convert;
                        })
                        .collect(Collectors.toList());

                if (CollectionUtils.isNotEmpty(advisoriesList)) {
                    devopsVulMapper.insert(advisoriesList);

                    List<DevopsVulPackage> devopsVulPackages = advisoriesList.stream().map(advisoriey -> {
                        List<DevopsVulPackage> affecteds = advisoriey.getAffecteds();
                        affecteds = affecteds.stream().map(affecte -> {
                            affecte.setVulId(advisoriey.getId());
                            return affecte;
                        }).collect(Collectors.toList());
                        return affecteds;
                    }).flatMap(List::stream).collect(Collectors.toList());
                    devopsVulPackageMapper.insert(devopsVulPackages);
                }
                log.info("【sync】devopsVulMapper.saveBatch: {}", (System.currentTimeMillis() - timeMillis));
                List<DevopsVul> updateAdvisoriesList = githubAdvisories1.stream()
                        .parallel()
                        .filter(githubAdvisories -> CollectionUtils.isNotEmpty(devopsVulList) && ghsaIds.contains(githubAdvisories.getGhsaId()) && !devopsVulListMap.get(githubAdvisories.getGhsaId()).equals(githubAdvisories.getModified()))
                        .map(githubAdvisories -> {
                            DevopsVul convert = getDevopsVul(githubAdvisories, gson);
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

    private static DevopsVul getDevopsVul(GithubAdvisories githubAdvisories, Gson gson) {
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
        convert.setRef(gson.toJson(githubAdvisories.getReferences()));
        if (CollectionUtils.isNotEmpty(githubAdvisories.getAffected())){
            List<GithubAffected> affected = githubAdvisories.getAffected();

            List<DevopsVulPackage> affecteds = Lists.newArrayList();
            for (GithubAffected githubAffected : affected) {
                DevopsVulPackage devopsVulPackage = new DevopsVulPackage();

                JsonObject githubAffectedPackages = githubAffected.getPackages();
                String ecosystem = gson.toJson(githubAffectedPackages.get("ecosystem")).replace("\"", "");
                convert.setEcosystem(ecosystem);
                devopsVulPackage.setEcosystem(ecosystem);
                String packages = gson.toJson(githubAffectedPackages.get("name")).replace("\"", "");
                convert.setPackages(packages);
                devopsVulPackage.setPackages(packages);

                List<Range> ranges = githubAffected.getRanges();
                if (CollectionUtils.isNotEmpty(ranges)) {
                    Range range = ranges.get(0);
                    List<AffectedEvent> events = range.getEvents();
                    AffectedEvent event = events.get(0);
                    convert.setIntroduced(event.getIntroduced());
                    devopsVulPackage.setIntroduced(event.getIntroduced());
                    if (events.size() > 1) {
                        AffectedEvent affectedEvent = events.get(1);
                        if (event.getIntroduced().equals("0") && !Strings.isNullOrEmpty(affectedEvent.getLastAffected())) {
                            convert.setIntroduced(String.format("<=%s", affectedEvent.getLastAffected()));
                            devopsVulPackage.setIntroduced(String.format("<=%s", affectedEvent.getLastAffected()));
                        }
                        convert.setFixed(affectedEvent.getFixed());
                        devopsVulPackage.setFixed(affectedEvent.getFixed());
                    }
                }
                affecteds.add(devopsVulPackage);
            }
            convert.setAffecteds(affecteds);
        }
        return convert;
    }

    private int getExitCode() throws IOException, InterruptedException {
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

        List<String> add = Lists.newArrayList();
        List<String> update = Lists.newArrayList();
        while ((line = reader.readLine()) != null) {
            if (line.contains(".json")) {
                String[] split = line.split("/");
                if (line.startsWith(" .../") || line.startsWith("advisories/")) {
                    update.add(split[split.length - 1]);
                }
                if (line.startsWith(" create mode")) {
                    add.add(split[split.length - 1]);
                }
            }
            log.info("【sync git advisory-database】:{}", line);
        }
        // 等待进程完成
        int exitCode = process.waitFor();
        return exitCode;
    }

}
