package com.hfwas.devops.controller.vul;

import com.google.gson.Gson;
import com.hfwas.devops.convert.DevopsVulConvert;
import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.tools.entity.github.GithubAdvisories;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hfwas
 * @package com.hfwas.devops.controller.vul
 * @date 2025/1/12
 */
@RestController
@RequestMapping("/vul")
@Slf4j
public class VulController {

    @Resource
    DevopsVulMapper devopsVulMapper;

    @GetMapping("/getById")
    public void getById() throws IOException {
        DevopsVul devopsVul = devopsVulMapper.selectById(495456L);
        String databaseSpecific = devopsVul.getDatabaseSpecific();
        log.info("databaseSpecific:{}", databaseSpecific);
    }

    @GetMapping("/sync")
    public void sync() throws IOException {
        long timeMillis = System.currentTimeMillis();
        log.info("start time:{}", timeMillis);
        Stream<Path> walk = Files.walk(Paths.get("/Users/hfwas/github/ghsa"));
        List<Path> collect = walk.parallel().filter(Files::isRegularFile).collect(Collectors.toList());
        Gson gson = new Gson();
        ArrayList<GithubAdvisories> githubAdvisories1 = new ArrayList<>();
        collect.stream().parallel().filter(path -> path.getFileName().toString().endsWith("json")).forEach(path -> {
            byte[] bytes = null;
            try {
                bytes = Files.readAllBytes(path);
                try {
                    GithubAdvisories githubAdvisories = gson.fromJson(new String(bytes), GithubAdvisories.class);
                    githubAdvisories1.add(githubAdvisories);
                } catch (Exception e) {
                    log.error("githubAdvisories:{}",new String(bytes),e);
                }
            } catch (IOException e) {
            }
        });

        List<DevopsVul> devopsVulList = devopsVulMapper.list(null);
        List<String> ghsaIds = devopsVulList.stream().parallel().map(DevopsVul::getGhsaId).collect(Collectors.toList());
        List<DevopsVul> advisoriesList = githubAdvisories1.stream()
                .parallel()
                .filter(githubAdvisories -> {
                    if (CollectionUtils.isEmpty(devopsVulList)){
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
                    convert.setServerity(gson.toJson(githubAdvisories.getSeverity()));
                    convert.setAffected(gson.toJson(githubAdvisories.getAffected()));
                    convert.setReferencess(gson.toJson(githubAdvisories.getReferences()));
                    convert.setDatabaseSpecific(gson.toJson(githubAdvisories.getDatabaseSpecific()));
                    if (CollectionUtils.isNotEmpty(githubAdvisories.getAffected())){
                        convert.setEcosystem(gson.toJson(githubAdvisories.getAffected().get(0).getPackages().get("ecosystem")));
                    }
                    devopsVulMapper.save(convert);
                    return convert;
                })
                .collect(Collectors.toList());
//        if (CollectionUtils.isNotEmpty(advisoriesList)) {
//            devopsVulMapper.saveBatch(advisoriesList);
//        }

        List<DevopsVul> updateAdvisoriesList = githubAdvisories1.stream()
                .parallel()
                .filter(githubAdvisories -> CollectionUtils.isNotEmpty(devopsVulList) && ghsaIds.contains(githubAdvisories.getGhsaId()))
                .map(githubAdvisories -> {
                    DevopsVul convert = DevopsVulConvert.INSTANCE.convert(githubAdvisories);
                    convert.setGhsaId(githubAdvisories.getGhsaId());
                    if (CollectionUtils.isNotEmpty(githubAdvisories.getAliases())){
                        convert.setCveId(githubAdvisories.getAliases().get(0));
                    }
                    convert.setServerity(gson.toJson(githubAdvisories.getSeverity()));
                    convert.setAffected(gson.toJson(githubAdvisories.getSeverity()));
                    convert.setReferencess(gson.toJson(githubAdvisories.getSeverity()));
                    convert.setDatabaseSpecific(gson.toJson(githubAdvisories.getSeverity()));
                    if (CollectionUtils.isNotEmpty(githubAdvisories.getAffected())){
                        convert.setEcosystem(gson.toJson(githubAdvisories.getAffected().get(0).getPackages().get("ecosystem").toString()));
                    }
                    return convert;
                })
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(updateAdvisoriesList)) {
            devopsVulMapper.updateBatch(updateAdvisoriesList);
        }
        log.info("end time:{}", (System.currentTimeMillis() - timeMillis));
    }

}
