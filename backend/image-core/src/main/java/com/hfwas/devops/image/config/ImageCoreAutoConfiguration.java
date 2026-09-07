package com.hfwas.devops.image.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.hfwas.devops.image")
@MapperScan("com.hfwas.devops.image.history.mapper")
public class ImageCoreAutoConfiguration {
}
