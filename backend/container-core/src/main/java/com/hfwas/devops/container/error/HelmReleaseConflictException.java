package com.hfwas.devops.container.error;

import com.hfwas.devops.common.error.BizException;

/** Release name is already in use in the namespace. Mapped to HTTP 409. */
public class HelmReleaseConflictException extends BizException {

    public HelmReleaseConflictException(String message) {
        super(ContainerErrorCode.HELM_RELEASE_EXISTS,
                message == null || message.isBlank()
                        ? ContainerErrorCode.HELM_RELEASE_EXISTS.getMessage()
                        : message);
    }
}
