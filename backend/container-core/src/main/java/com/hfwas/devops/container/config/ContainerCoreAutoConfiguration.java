package com.hfwas.devops.container.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration entry for container-core module.
 */
@Configuration
@EnableScheduling
@MapperScan(value = "com.hfwas.devops.container.mapper", markerInterface = BaseMapper.class)
public class ContainerCoreAutoConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}