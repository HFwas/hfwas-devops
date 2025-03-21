package com.hfwas.devops.service.vul.php;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.php
 * @date 2025/1/14
 */
@Service("DevopsPhpJsonDepenScan")
public class DevopsPhpJsonDepenScan extends AbstractDepenScan implements InitializingBean {
    @Override
    public String language() {
        return "Php";
    }

    @Override
    public String type() {
        return "composer.json";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("Pub", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        byte[] bytes = multipartFile.getBytes();
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
