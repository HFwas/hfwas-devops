package com.hfwas.devops.service.vul.npm;

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
import java.util.stream.Collectors;

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
        Path path = Paths.get("/Users/houfei/workspace/demo-vue/yarn2.lock");
        List<String> readAllLines = Files.readAllLines(path);
        // yarn.lock 文件前四行是无用数据
        readAllLines = readAllLines.stream().skip(4).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            String readAllLine = readAllLines.get(i);
            DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
            if (!readAllLine.equals("") && !readAllLine.startsWith(" ")) {
                if (readAllLine.contains("/")) {
                    String[] split = readAllLine.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    String s = split[1];
                    String[] split1 = s.split("@");
                    devopsVulDependency.setDependencyName(split1[0]);
                    String version = readAllLines.get(i + 1);
                    version = version.substring(11).replace("\"", "");
                    devopsVulDependency.setVersion(version);
                    devopsVulDependencies.add(devopsVulDependency);
                } else {
                    String[] split1 = readAllLine.split("@");
                    devopsVulDependency.setCompany(split1[0]);
                    String version = readAllLines.get(i + 1).substring(11).replace("\"", "");
                    devopsVulDependency.setVersion(version);
                    devopsVulDependencies.add(devopsVulDependency);
                }
            }
        }
        return devopsVulDependencies;
    }

}
