package com.hfwas.devops.common.core.base;

import jdk.jfr.DataAmount;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.common.core.base
 * @date 2025/1/4
 */
@Data
@AllArgsConstructor
public class BaseResult<T> {
    private Integer code;
    private String msg;
    private T data;
}
