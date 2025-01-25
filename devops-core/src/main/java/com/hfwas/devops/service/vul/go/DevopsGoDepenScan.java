package com.hfwas.devops.service.vul.go;

import com.google.common.collect.Lists;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul.go
 * @date 2025/1/14
 */
@Service("DevopsGoDepenScan")
@Slf4j
public class DevopsGoDepenScan extends AbstractDepenScan implements InitializingBean {

    @Override
    public String language() {
        return "Go";
    }

    @Override
    public String type() {
        return "go";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("Go", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        byte[] bytes = multipartFile.getBytes();
        List<String> readAllLines = Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")).toList();
        List<DevopsVulCodeDependency> devopsVulDependencys = Lists.newArrayList();

        readAllLines = readAllLines.stream().skip(6).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
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
                devopsVulDependency.setType(3);
                devopsVulDependencys.add(devopsVulDependency);
            }
        }
        return devopsVulDependencys;
    }
}
