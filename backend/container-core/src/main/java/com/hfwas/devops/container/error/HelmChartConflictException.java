package com.hfwas.devops.container.error;

import com.hfwas.devops.common.error.BizException;

/**
 * Same chart name and version already exists in the repository or in Harbor OCI.
 * The upload API maps this to HTTP 409.
 */
public class HelmChartConflictException extends BizException {

    public HelmChartConflictException(String message) {
        super(ContainerErrorCode.HELM_CHART_VERSION_EXISTS,
                message == null || message.isBlank()
                        ? ContainerErrorCode.HELM_CHART_VERSION_EXISTS.getMessage()
                        : message);
    }
}
