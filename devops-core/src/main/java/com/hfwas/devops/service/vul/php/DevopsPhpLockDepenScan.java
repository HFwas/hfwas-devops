package com.hfwas.devops.service.vul.php;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.php
 * @date 2025/1/14
 */
@Service("DevopsPhpLockDepenScan")
public class DevopsPhpLockDepenScan extends AbstractDepenScan {
    @Override
    public String language() {
        return "Php";
    }

    @Override
    public String type() {
        return "composer.lock";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("composer.lock");
        byte[] bytes = Files.readAllBytes(path);
        Gson gson = new Gson();
        JsonObject composerJson = gson.fromJson(new String(bytes), JsonObject.class);
        if (composerJson.has("packages")) {
            JsonArray packages = composerJson.getAsJsonArray("packages");
            if (Objects.nonNull(packages) && packages.size()!=0) {

            }
        }
        if (composerJson.has("packages-dev")) {
            JsonArray packagesDev = composerJson.getAsJsonArray("packages-dev");
            if (Objects.nonNull(packagesDev) && packagesDev.size()!=0) {
                for (JsonElement jsonElement : packagesDev) {
                    DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
                    JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    String name = asJsonObject.getAsJsonPrimitive("name").getAsString();
                    if (name.contains("/")) {
                        String[] split = name.split("/");
                        devopsVulDependency.setCompany(split[0]);
                        devopsVulDependency.setDependencyName(split[1]);
                    }
                    String version = asJsonObject.getAsJsonPrimitive("version").getAsString();
                    devopsVulDependency.setVersion(version);
                    devopsVulDependencies.add(devopsVulDependency);
                }
            }
        }
        return devopsVulDependencies;
    }
}
