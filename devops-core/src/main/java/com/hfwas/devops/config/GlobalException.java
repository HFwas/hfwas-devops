package com.hfwas.devops.config;

import com.hfwas.devops.common.core.base.BaseResult;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/1/8
 */
@Slf4j
@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(value = FeignException.class)
    public BaseResult exception(Exception e) {
        log.error(e.getMessage(), e);
        if (e instanceof FeignException.BadGateway) {
            return new BaseResult<>(0, "网络不通", "");
        }
        if (e instanceof FeignException.BadRequest) {
            return new BaseResult<>(0, "参数不对", "");
        }
        return new BaseResult<>(0, e.getMessage(), "");
    }

    @ExceptionHandler(value = SQLSyntaxErrorException.class)
    public BaseResult sqlSyntaxErrorException(SQLSyntaxErrorException e) {
        return new BaseResult<>(0, e.getMessage(), "");
    }

    @ExceptionHandler(value = SQLException.class)
    public BaseResult SQLException(SQLException e) {
        if (e.getMessage().contains("Column count doesn't match value count at row")) {
            return new BaseResult<>(1, "实体类和数据库字段类型匹配不上", "");
        }
        return new BaseResult<>(0, e.getMessage(), "");
    }

    @ExceptionHandler(value = TooManyResultsException.class)
    public BaseResult tooManyResultsException(TooManyResultsException e) {
        log.error(e.getMessage(), e);
        return new BaseResult<>(1, "期待一条但是返回多条数据", "");
    }

}
