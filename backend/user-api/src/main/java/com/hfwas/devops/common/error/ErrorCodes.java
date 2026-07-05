package com.hfwas.devops.common.error;

/** Convenience throw helpers for services. */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static BizException ex(ErrorCode code) {
        return BizException.of(code);
    }

    public static BizException ex(ErrorCode code, String message) {
        return BizException.of(code, message);
    }

    public static void check(boolean condition, ErrorCode code) {
        if (!condition) {
            throw ex(code);
        }
    }

    public static void check(boolean condition, ErrorCode code, String message) {
        if (!condition) {
            throw ex(code, message);
        }
    }
}
