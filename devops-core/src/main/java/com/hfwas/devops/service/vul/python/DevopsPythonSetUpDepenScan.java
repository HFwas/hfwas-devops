package com.hfwas.devops.service.vul.python;

import com.google.common.collect.Lists;
import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
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
 * @package com.hfwas.devops.service.vul.python
 * @date 2025/1/14
 */
@Service("DevopsPythonSetUpDepenScan")
public class DevopsPythonSetUpDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "python";
    }

    @Override
    public String type() {
        return "setup.py";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        Path path = Paths.get("setup.py");
        List<String> readAllLines = Files.readAllLines(path, Charset.forName("UTF-8"));
        List<DevopsVulDependency> devopsVulDependencys = Lists.newArrayList();

        readAllLines = readAllLines.stream().skip(6).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            DevopsVulDependency devopsVulDependency = new DevopsVulDependency();
            String readLine = readAllLines.get(i);
            if (readLine.startsWith("    '")) {
                readLine = readLine.replace("    '", "");
                readLine = readLine.substring(0, readLine.length() - 2);
                String[] split = readLine.split("==");
                devopsVulDependency.setCompany(split[0]);
                devopsVulDependency.setVersion(split[1]);
                devopsVulDependencys.add(devopsVulDependency);
            }
        }
        return devopsVulDependencys;
    }
}
