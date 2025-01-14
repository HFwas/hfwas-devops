package com.hfwas.devops.service.vul.rust;

import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.rust
 * @date 2025/1/14
 */
@Service("DevopsRustDepenScan")
@Slf4j
public class DevopsRustDepenScan extends AbstractDepenScan {
    @Override
    public String language() {
        return "Rust";
    }

    @Override
    public String type() {
        return "Cargo.toml";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulDependency> devopsVulDependencies = new ArrayList<>();
        Path path = Paths.get("Cargo.toml");
        List<String> readAllLines = Files.readAllLines(path);
        int start = readAllLines.indexOf("[dependencies]");
        for (int i = start + 1; i < readAllLines.size(); i++) {
            String readAllLine = readAllLines.get(i);
            if (readAllLine.equals("") || readAllLine.startsWith("[")) {
                break;
            }
            DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
            String[] split = readAllLine.split(" = ");
            devopsVulDependency.setCompany(split[0]);
            if (split.length ==  2) {
                String version = split[1].replace("\"", "");
                devopsVulDependency.setVersion(version);
            } else {
                String[] split1 = split[2].replace("\"", "").split(",");
                devopsVulDependency.setVersion(split1[0]);
            }
            devopsVulDependencies.add(devopsVulDependency);
        }
        return devopsVulDependencies;
    }
}
