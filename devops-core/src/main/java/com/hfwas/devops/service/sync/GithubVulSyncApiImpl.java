package com.hfwas.devops.service.sync;

import com.hfwas.devops.tools.api.vulnerability.GithubAdvisoriesApi;
import com.hfwas.devops.tools.entity.githubApi.SecurityAdvisory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.sync
 * @date 2025/3/5
 */
@Service("GithubVulSyncApiImpl")
@Slf4j
public class GithubVulSyncApiImpl implements VulSyncApi{

    @Resource
    private GithubAdvisoriesApi githubAdvisoriesApi;

    @Override
    public void sync() {
        List<SecurityAdvisory> advisories = githubAdvisoriesApi.advisories("2022-11-28", null, null, null, null, null, null);
        for (SecurityAdvisory advisory : advisories) {
            log.info("advisory: {}", advisory);
        }
    }
}
