package com.hfwas.devops.service.vul.npm;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsYarnDepenScan")
public class DevopsYarnDepenScan extends AbstractDepenScan implements InitializingBean {

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "yarn";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        byte[] bytes = multipartFile.getBytes();
        List<String> readAllLines = Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")).toList();
        // yarn.lock 文件前四行是无用数据
        readAllLines = readAllLines.stream().skip(4).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            String readAllLine = readAllLines.get(i);
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
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
                } else {
                    String[] split1 = readAllLine.split("@");
                    devopsVulDependency.setCompany(split1[0]);
                    String version = readAllLines.get(i + 1).substring(11).replace("\"", "");
                    devopsVulDependency.setVersion(version);
                }
                devopsVulDependency.setType(2);
                devopsVulDependencies.add(devopsVulDependency);
            }
        }
        return devopsVulDependencies;
    }

}
