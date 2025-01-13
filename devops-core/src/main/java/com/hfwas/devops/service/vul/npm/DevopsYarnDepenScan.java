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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsYarnDepenScan")
public class DevopsYarnDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "yarn";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("yarn1.lock");
        InputStream inputStream = Files.newInputStream(path, StandardOpenOption.CREATE_NEW);
        Yaml yaml = new Yaml();
        Map<String, Object> yarnLockData = yaml.load(inputStream);

        return devopsVulDependencies;
    }

}
