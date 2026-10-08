package com.hfwas.devops.container.error;

import com.hfwas.devops.common.error.BizException;

/** Mapped to HTTP 404. */
public class HelmReleaseNotFoundException extends BizException {

    public HelmReleaseNotFoundException(String message) {
        super(ContainerErrorCode.HELM_RELEASE_NOT_FOUND,
                message == null || message.isBlank()
                        ? ContainerErrorCode.HELM_RELEASE_NOT_FOUND.getMessage()
                        : message);
    }
}
