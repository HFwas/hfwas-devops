package com.hfwas.devops.config;

import com.hfwas.devops.handler.SqlInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/1/16
 */
@Configuration
public class MybatisConfig {

    @Bean
    public Interceptor sqlInterceptor() {
        return new SqlInterceptor();
    }

}
