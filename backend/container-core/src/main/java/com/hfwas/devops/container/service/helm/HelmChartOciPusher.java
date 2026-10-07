package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.container.error.HelmChartConflictException;

/**
 * Pushes a packaged chart to Harbor OCI.
 * Tests substitute an in-memory implementation; production uses {@link HelmCliOciPusher}.
 */
public interface HelmChartOciPusher {

    /**
     * @throws HelmChartConflictException when the tag already exists remotely
     */
    OciPushResult push(OciPushRequest request);
}
