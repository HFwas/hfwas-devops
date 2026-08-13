package com.hfwas.devops.apitest.common.exception;

import lombok.Getter;

/**
 * 接口测试模块异常
 *
 * @author hfwas
 */
@Getter
public class ApiTestException extends RuntimeException {

    private final Integer code;

    public ApiTestException(String message) {
        super(message);
        this.code = 400;
    }

    public ApiTestException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public ApiTestException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}