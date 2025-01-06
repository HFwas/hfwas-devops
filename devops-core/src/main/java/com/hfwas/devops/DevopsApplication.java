package com.hfwas.devops;

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
@EnableFeignClients(basePackages = {"com.hfwas.devops"})
@ImportAutoConfiguration({FeignAutoConfiguration.class})
public class DevopsApplication {
    public static void main( String[] args ) {
        SpringApplication.run(DevopsApplication.class, args);
    }
}
