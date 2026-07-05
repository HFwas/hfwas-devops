package com.hfwas.devops.common.core.base;

import com.hfwas.devops.common.error.ErrorCode;
import com.hfwas.devops.common.error.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResult<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> BaseResult<T> ok() {
        return restResult(ResultCode.SUCCESS.getCode(), null, null);
    }

    public static <T> BaseResult<T> ok(T data) {
        return restResult(ResultCode.SUCCESS.getCode(), null, data);
    }

    public static <T> BaseResult<T> ok(T data, String msg) {
        return restResult(ResultCode.SUCCESS.getCode(), msg, data);
    }

    /** @deprecated use {@link #failed(ErrorCode)} or {@link #failed(int, String)} */
    @Deprecated
    public static <T> BaseResult<T> failed() {
        return failed(ResultCode.BAD_REQUEST);
    }

    /** @deprecated use {@link #failed(ErrorCode, String)} or {@link #failed(int, String)} */
    @Deprecated
    public static <T> BaseResult<T> failed(String msg) {
        return failed(ResultCode.BAD_REQUEST, msg);
    }

    public static <T> BaseResult<T> failed(ErrorCode errorCode) {
        return restResult(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> BaseResult<T> failed(ErrorCode errorCode, String msg) {
        return restResult(errorCode.getCode(), msg, null);
    }

    public static <T> BaseResult<T> failed(int code, String msg) {
        return restResult(code, msg, null);
    }

    public boolean isSuccess() {
        return code != null && code == ResultCode.SUCCESS.getCode();
    }

    private static <T> BaseResult<T> restResult(int code, String msg, T data) {
        BaseResult<T> apiResult = new BaseResult<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }
}
