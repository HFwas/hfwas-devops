package com.hfwas.devops.pipeline.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.hfwas.devops.pipeline")
@MapperScan("com.hfwas.devops.pipeline.mapper")
public class PipelineCoreAutoConfiguration {
}
