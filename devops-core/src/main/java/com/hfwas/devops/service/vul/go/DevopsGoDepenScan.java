package com.hfwas.devops.service.vul.go;

import com.google.common.collect.Lists;
import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.go
 * @date 2025/1/14
 */
@Service("DevopsGoDepenScan")
@Slf4j
public class DevopsGoDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "Go";
    }

    @Override
    public String type() {
        return "go";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        Path path = Paths.get("go.mod");
        List<String> readAllLines = Files.readAllLines(path, Charset.forName("UTF-8"));
        List<DevopsVulDependency> devopsVulDependencys = Lists.newArrayList();

        readAllLines = readAllLines.stream().skip(6).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
            String readLine = readAllLines.get(i);
            if (!readLine.equals(")") &&
                    !readLine.contains(" => ") &&
                    !readLine.contains("go ") &&
                    !readLine.equals("") &&
                    !readLine.contains("require") &&
                    !readLine.contains("module ")) {
                String replace = readLine.replace("\t", "").replace(" // indirect","");
                String[] split = replace.split("/");
                if (split.length == 3) {
                    devopsVulDependency.setCompany(String.format("%s/%s", split[0], split[1]));
                } else {
                    devopsVulDependency.setCompany(split[0]);
                }
                String[] split1 = split[split.length - 1].split(" ");
                devopsVulDependency.setDependencyName(split1[0]);
                devopsVulDependency.setVersion(split1[1]);
                devopsVulDependencys.add(devopsVulDependency);
            }
        }
        return devopsVulDependencys;
    }

}
