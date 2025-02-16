package com.hfwas.devops.service.codeScan.sonarqube;

import com.hfwas.devops.service.codeScan.CodeScanApi;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author houfei
 * @package com.hfwas.devops.service.codeScan.sonarqube
 * @date 2025/2/12
 */
@Service("SonarqubeCodeScan")
public class SonarqubeCodeScan implements CodeScanApi {

    @Override
    public void resolve(MultipartFile multipartFile) {

    }

}
