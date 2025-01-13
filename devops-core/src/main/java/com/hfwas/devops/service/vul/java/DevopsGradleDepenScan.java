package com.hfwas.devops.service.vul.java;

import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.java
 * @date 2025/1/13
 */
@Service("DevopsGradleDepenScan")
public class DevopsGradleDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "Java";
    }

    @Override
    public String type() {
        return "maven";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) {
        return List.of();
    }

}
