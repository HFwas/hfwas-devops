package com.hfwas.devops.service.vul.npm;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsPnpmDepenScan")
public class DevopsPnpmDepenScan extends AbstractDepenScan implements InitializingBean {

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "pnpm";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        InputStream inputStream = multipartFile.getInputStream();
        Yaml yaml = new Yaml();
        Map<String, HashMap<String, HashMap>> yarnLockData = yaml.load(inputStream);
        HashMap<String, HashMap> jsonObject = yarnLockData.get("packages");
        Set<String> keySet = jsonObject.keySet();
        for (String s : keySet) {
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
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
