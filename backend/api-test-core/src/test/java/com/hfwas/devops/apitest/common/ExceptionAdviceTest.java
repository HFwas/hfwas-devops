package com.hfwas.devops.apitest.common;

import com.hfwas.devops.common.core.base.BaseResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionAdviceTest {

    @Test
    void baseResultError_returnsCorrectCode() {
        BaseResult<Void> result = BaseResult.failed(400, "业务错误");
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("业务错误");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void baseResultError_500() {
        BaseResult<Void> result = BaseResult.failed(500, "服务器错误");
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.isSuccess()).isFalse();
    }
}