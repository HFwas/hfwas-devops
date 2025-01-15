package com.hfwas.devops.service.vul.npm;

import com.google.gson.Gson;
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
import java.util.Set;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsNpmDepenScan")
public class DevopsNpmDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "npm";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("package-lock2025010109.json");
        byte[] bytes = Files.readAllBytes(path);
        Gson gson = new Gson();
        JsonObject npmJson = gson.fromJson(new String(bytes), JsonObject.class);
        JsonObject asJsonObject = npmJson.getAsJsonObject("packages");

        JsonObject nullJsonObject = asJsonObject.getAsJsonObject("");
        if (nullJsonObject.has("devDependencies")) {
            JsonObject devDependencies = nullJsonObject.getAsJsonObject("devDependencies");
            Set<String> keySet = devDependencies.keySet();
        }

        Set<String> keySet = asJsonObject.keySet();
        for (String key : keySet) {
            if (key.equals("")) continue;
            String version = asJsonObject.getAsJsonObject(key).get("version").getAsString();
            if (key.startsWith("node_modules")) {
                DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
                String substring = key.substring("node_modules".length()+1);
                boolean contains = substring.contains("/");
                if (contains) {
                    String[] split = substring.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    devopsVulDependency.setDependencyName(split[1]);
                    devopsVulDependency.setVersion(version);
                    devopsVulDependency.setType(2);
                    devopsVulDependencies.add(devopsVulDependency);
                } else {
                    String[] split = substring.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    devopsVulDependency.setDependencyName(null);
                    devopsVulDependency.setVersion(version);
                    devopsVulDependency.setType(2);
                    devopsVulDependencies.add(devopsVulDependency);
                }
            }
        }
        return devopsVulDependencies;
    }

}
