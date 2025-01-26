package com.hfwas.devops.controller.vulDependency;

import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vulDependency.VulDepService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vulDependency
 * @date 2025/1/25
 */
@RestController
@RequestMapping("/vulDep")
public class VulDepController {

    @Resource
    VulDepService vulDepService;

    @PostMapping(value = "/depResolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DevopsVulCodeDependency> depResolve(@RequestPart("file") MultipartFile multipartFile, @RequestParam("language") String language) throws IOException {
        List<DevopsVulCodeDependency> codeDependencies = vulDepService.depResolve(multipartFile, language);
        return codeDependencies;
    }

    @PostMapping(value = "/dep")
    public List<DevopsVulCodeDependency> dep(@RequestParam Long codeId) {
        List<DevopsVulCodeDependency> dep = vulDepService.dep(codeId);
        return dep;
    }

}
