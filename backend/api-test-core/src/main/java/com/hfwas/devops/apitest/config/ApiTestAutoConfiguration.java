package com.hfwas.devops.apitest.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 接口测试模块自动配置
 *
 * @author hfwas
 */
@Configuration
@ComponentScan("com.hfwas.devops.apitest")
@MapperScan("com.hfwas.devops.apitest.**.mapper")
public class ApiTestAutoConfiguration {

}