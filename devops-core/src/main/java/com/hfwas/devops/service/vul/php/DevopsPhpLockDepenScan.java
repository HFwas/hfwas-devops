package com.hfwas.devops.service.vul.php;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.php
 * @date 2025/1/14
 */
@Service("DevopsPhpLockDepenScan")
public class DevopsPhpLockDepenScan extends AbstractDepenScan implements InitializingBean {
    @Override
    public String language() {
        return "Php";
    }

    @Override
    public String type() {
        return "composer.lock";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        byte[] bytes = multipartFile.getBytes();
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
                    DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
                    JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    String name = asJsonObject.getAsJsonPrimitive("name").getAsString();
                    if (name.contains("/")) {
                        String[] split = name.split("/");
                        devopsVulDependency.setCompany(split[0]);
                        devopsVulDependency.setDependencyName(split[1]);
                    }
                    String version = asJsonObject.getAsJsonPrimitive("version").getAsString();
                    devopsVulDependency.setVersion(version);
                    devopsVulDependency.setType(5);
                    devopsVulDependencies.add(devopsVulDependency);
                }
            }
        }
        return devopsVulDependencies;
    }
}
