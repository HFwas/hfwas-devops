package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.common.core.base.BaseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.FileNotFoundException;
import java.sql.SQLException;

/**
 * @author houfei
 * @package com.hfwas.devops.common.core.exception
 * @date 2025/6/17
 */
@RestControllerAdvice
@Slf4j
public class ExceptionAdvice {

    @ExceptionHandler(FileNotFoundException.class)
    public BaseResult handleFileNotFound(FileNotFoundException e) {
        log.error(e.getMessage(), e);
        // 错误信息发送通知

        return BaseResult.failed();
    }

    @ExceptionHandler(SQLException.class)
    public BaseResult handleFileNotFound(SQLException e) {
        log.error(e.getMessage(), e);
        // 错误信息发送通知

        return BaseResult.failed();
    }

}
