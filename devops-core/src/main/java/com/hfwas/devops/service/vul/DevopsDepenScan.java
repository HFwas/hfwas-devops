package com.hfwas.devops.service.vul;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul
 * @date 2025/1/13
 */
public interface DevopsDepenScan {

    String language();

    String type();

    List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException;

    List<String> depenVersion(String depen, String version);
}
