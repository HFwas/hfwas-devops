package com.hfwas.devops.service.vul.php;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.php
 * @date 2025/1/14
 */
@Service("DevopsPhpJsonDepenScan")
public class DevopsPhpJsonDepenScan extends AbstractDepenScan {
    @Override
    public String language() {
        return "Php";
    }

    @Override
    public String type() {
        return "composer.json";
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("composer.json");
        byte[] bytes = Files.readAllBytes(path);
        Gson gson = new Gson();
        JsonObject npmJson = gson.fromJson(new String(bytes), JsonObject.class);
        JsonObject asJsonObject = npmJson.getAsJsonObject("conflict");
        Set<String> depens = asJsonObject.keySet();
        for (String depen : depens) {
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
            String[] split = depen.split("/");
            devopsVulDependency.setCompany(split[0]);
            devopsVulDependency.setDependencyName(split[1]);

            String version = asJsonObject.getAsJsonPrimitive(depen).getAsString();
            devopsVulDependency.setVersion(version);
            devopsVulDependency.setType(5);
            devopsVulDependencies.add(devopsVulDependency);
        }
        return devopsVulDependencies;
    }
}
