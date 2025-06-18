package com.hfwas.devops.controller.vulDependency;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vulDependency.VulDepService;
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

    private final VulDepService vulDepService;

    public  VulDepController(VulDepService vulDepService) {
        this.vulDepService = vulDepService;
    }

    @PostMapping(value = "/depResolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResult<List<DevopsVulCodeDependency>> depResolve(@RequestPart("file") MultipartFile multipartFile, @RequestParam("language") String language) throws IOException {
        List<DevopsVulCodeDependency> codeDependencies = vulDepService.depResolve(multipartFile, language);
        return BaseResult.ok(codeDependencies);
    }

    @PostMapping(value = "/dep")
    public BaseResult<List<DevopsVulCodeDependency>> dep(@RequestParam Long codeId) {
        List<DevopsVulCodeDependency> dep = vulDepService.dep(codeId);
        return BaseResult.ok(dep);
    }

}
