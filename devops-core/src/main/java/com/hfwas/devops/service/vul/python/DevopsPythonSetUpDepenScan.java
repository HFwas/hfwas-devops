package com.hfwas.devops.service.vul.python;

import com.google.common.collect.Lists;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
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
 * @package com.hfwas.devops.service.vul.python
 * @date 2025/1/14
 */
@Service("DevopsPythonSetUpDepenScan")
public class DevopsPythonSetUpDepenScan extends AbstractDepenScan implements InitializingBean {

    @Override
    public String language() {
        return "python";
    }

    @Override
    public String type() {
        return "setup.py";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("PyPI", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        byte[] bytes = multipartFile.getBytes();
        List<String> readAllLines = Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")).toList();;
        List<DevopsVulCodeDependency> devopsVulDependencys = Lists.newArrayList();

        readAllLines = readAllLines.stream().skip(6).collect(Collectors.toList());
        for (int i = 0; i < readAllLines.size(); i++) {
            DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
            String readLine = readAllLines.get(i);
            if (readLine.startsWith("    '")) {
                readLine = readLine.replace("    '", "");
                readLine = readLine.substring(0, readLine.length() - 2);
                String[] split = readLine.split("==");
                devopsVulDependency.setCompany(split[0]);
                devopsVulDependency.setVersion(split[1]);
                devopsVulDependency.setType(4);
                devopsVulDependencys.add(devopsVulDependency);
            }
        }
        return devopsVulDependencys;
    }
}
