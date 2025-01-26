package com.hfwas.devops.service.vulDependency;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.mapper.DevopsVulDependencyMapper;
import com.hfwas.devops.service.vul.DepenScanFactory;
import com.hfwas.devops.service.vul.DevopsDepenScan;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vulDependency
 * @date 2025/1/26
 */
@Service
public class VulDepService {

    @Resource
    DevopsVulDependencyMapper dependencyMapper;

    public List<DevopsVulCodeDependency> depResolve(MultipartFile multipartFile, String language) throws IOException {
        DevopsDepenScan bySceneCode = DepenScanFactory.getByLanguage(language);
        List<DevopsVulCodeDependency> dependencys = bySceneCode.dependencys(multipartFile);
        return dependencys;
    }

    public List<DevopsVulCodeDependency> dep(Long codeId) {

        return null;
    }

}
