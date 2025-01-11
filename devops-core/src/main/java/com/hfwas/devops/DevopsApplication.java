package com.hfwas.devops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.hfwas"})
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@MapperScan(basePackages = {"com.hfwas.devops.mapper"})
public class DevopsApplication {
    public static void main( String[] args ) {
        SpringApplication.run(DevopsApplication.class, args);
    }
}
