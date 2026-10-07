package com.hfwas.devops.container.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import com.hfwas.devops.container.config.argo.ArgoWorkflowProperties;
import com.hfwas.devops.container.service.helm.HelmChartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration entry for container-core module.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ArgoWorkflowProperties.class, HelmChartProperties.class})
@MapperScan(value = "com.hfwas.devops.container.mapper", markerInterface = BaseMapper.class)
public class ContainerCoreAutoConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}