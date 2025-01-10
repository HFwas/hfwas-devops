package com.hfwas.devops.controller;

import com.google.gson.Gson;
import com.hfwas.devops.tools.api.nexus.NexusUserApi;
import com.hfwas.devops.tools.api.vulnerability.AvdApi;
import com.hfwas.devops.tools.api.vulnerability.GithubAdvisoriesApi;
import com.hfwas.devops.tools.entity.github.GithubAdvisories;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/1/6
 */
@RestController
@RequestMapping("/demo")
@Slf4j
public class DemoController {

    @Autowired
    private NexusUserApi nexusUserApi;
    @Autowired
    AvdApi avdApi;
    @Autowired
    GithubAdvisoriesApi githubAdvisoriesApi;

    @GetMapping("/advisories")
    public long advisories() throws IOException, InterruptedException {
        long timeMillis = System.currentTimeMillis();
        log.info("start time:{}", timeMillis);
        Stream<Path> walk = Files.walk(Paths.get("/Users/hfwas/ghsa/advisories"));
        List<Path> collect = walk.parallel().filter(Files::isRegularFile).collect(Collectors.toList());
        Gson gson = new Gson();
        ArrayList<GithubAdvisories> githubAdvisories1 = new ArrayList<>();
        collect.stream().parallel().forEach(path -> {
            byte[] bytes = null;
            try {
                bytes = Files.readAllBytes(path);
                GithubAdvisories githubAdvisories = gson.fromJson(new String(bytes), GithubAdvisories.class);
                githubAdvisories1.add(githubAdvisories);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Map<String, GithubAdvisories> elementJsonObjectMap = githubAdvisories1.stream().parallel().collect(Collectors.toMap(jsonObject -> jsonObject.getId(), Function.identity()));
        log.info("end time:{}", (System.currentTimeMillis() - timeMillis));
        return (System.currentTimeMillis() - timeMillis);
    }


}
