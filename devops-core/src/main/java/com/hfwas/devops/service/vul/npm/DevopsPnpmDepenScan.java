package com.hfwas.devops.service.vul.npm;

import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsPnpmDepenScan")
public class DevopsPnpmDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "pnpm";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("pnpm-lock1.yaml");
        InputStream inputStream = Files.newInputStream(path, StandardOpenOption.CREATE_NEW);
        Yaml yaml = new Yaml();
        Map<String, HashMap<String, HashMap>> yarnLockData = yaml.load(inputStream);
        HashMap<String, HashMap> jsonObject = yarnLockData.get("packages");
        Set<String> keySet = jsonObject.keySet();
        for (String s : keySet) {
            DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
            HashMap asJsonObject = jsonObject.get(s);
            if (s.startsWith("/")) {
                String substring = s.substring(1);
                if (substring.contains("/")) {
                    String[] split = substring.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    String s1 = split[1];
                    String[] split1 = s1.split("@");
                    devopsVulDependency.setDependencyName(split1[0]);
                    devopsVulDependency.setVersion(split1[1]);
                } else {
                    String[] split = substring.split("@");
                    devopsVulDependency.setCompany(split[0]);
                    devopsVulDependency.setVersion(split[1]);
                }
                devopsVulDependency.setType(2);
                devopsVulDependencies.add(devopsVulDependency);
            }
        }
        return devopsVulDependencies;
    }

}
