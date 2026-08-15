package com.hfwas.devops.apitest.common;

import com.hfwas.devops.common.core.base.BaseResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BaseResultTest {

    @Test
    void ok_returnsSuccess() {
        BaseResult<String> result = BaseResult.ok("data");
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo("data");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void ok_noData_returnsSuccess() {
        BaseResult<Void> result = BaseResult.ok();
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failed_withMessage_returnsError() {
        BaseResult<Void> result = BaseResult.failed(400, "参数错误");
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("参数错误");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void failed_withSuccessFalse() {
        BaseResult<Void> result = BaseResult.failed(500, "服务器错误");
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.isSuccess()).isFalse();
    }
}