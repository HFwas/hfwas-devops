package com.hfwas.devops.apitest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 接口测试平台 — 测试应用入口
 * <p>
 * 用于单元测试启动 Spring 上下文
 *
 * @author hfwas
 */
@SpringBootApplication
@ComponentScan("com.hfwas.devops.apitest")
@MapperScan("com.hfwas.devops.apitest.**.mapper")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}