package com.hfwas.devops.container.service.helm;

import java.nio.file.Path;

/**
 * Downloads a chart package from OCI. Tests substitute a fake so Harbor is not required.
 */
public interface HelmChartPackagePuller {

    Path pull(OciPullRequest request);
}
