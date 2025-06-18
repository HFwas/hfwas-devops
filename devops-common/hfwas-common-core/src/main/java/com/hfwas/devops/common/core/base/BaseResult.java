package com.hfwas.devops.common.core.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author houfei
 * @package com.hfwas.devops.common.core.base
 * @date 2025/1/4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResult<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> BaseResult<T> ok() {
        return restResult(0, "", null);
    }

    public static <T> BaseResult<T> ok(T data) {
        return restResult(0, null, data);
    }

    public static <T> BaseResult<T> ok(T data, String msg) {
        return restResult(0, msg, data);
    }

    public static <T> BaseResult<T> failed() {
        return restResult(1, null, null);
    }

    public static <T> BaseResult<T> failed(String msg) {
        return restResult(1, msg, null);
    }

    public static <T> BaseResult<T> failed(T data) {
        return restResult(1, null, data);
    }

    public static <T> BaseResult<T> failed(T data, String msg) {
        return restResult(1, msg, data);
    }

    private static <T> BaseResult<T> restResult(int code, String msg, T data) {
        BaseResult<T> apiResult = new BaseResult<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }
}
