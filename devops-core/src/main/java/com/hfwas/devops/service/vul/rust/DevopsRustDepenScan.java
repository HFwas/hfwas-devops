package com.hfwas.devops.service.vul.rust;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.rust
 * @date 2025/1/14
 */
@Service("DevopsRustDepenScan")
@Slf4j
public class DevopsRustDepenScan extends AbstractDepenScan implements InitializingBean {
    @Override
    public String language() {
        return "Rust";
    }

    @Override
    public String type() {
        return "Cargo.toml";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("RubyGems", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        byte[] bytes = multipartFile.getBytes();
        List<String> readAllLines = Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")).toList();;
        int start = readAllLines.indexOf("[dependencies]");
        for (int i = start + 1; i < readAllLines.size(); i++) {
            String readAllLine = readAllLines.get(i);
            if (readAllLine.equals("") || readAllLine.startsWith("[")) {
                break;
            }
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
            String[] split = readAllLine.split(" = ");
            devopsVulDependency.setCompany(split[0]);
            if (split.length ==  2) {
                String version = split[1].replace("\"", "");
                devopsVulDependency.setVersion(version);
            } else {
                String[] split1 = split[2].replace("\"", "").split(",");
                devopsVulDependency.setVersion(split1[0]);
            }
            devopsVulDependency.setType(6);
            devopsVulDependencies.add(devopsVulDependency);
        }
        return devopsVulDependencies;
    }
}
