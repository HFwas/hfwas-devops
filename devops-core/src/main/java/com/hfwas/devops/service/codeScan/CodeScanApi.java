package com.hfwas.devops.service.codeScan;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author houfei
 * @package com.hfwas.devops.service.codeScan
 * @date 2025/2/12
 */
public interface CodeScanApi {

    void resolve(MultipartFile multipartFile);

}
