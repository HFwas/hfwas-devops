package com.hfwas.devops.pm.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.hfwas.devops.pm")
@MapperScan(value = "com.hfwas.devops.pm", markerInterface = BaseMapper.class)
public class PmCoreAutoConfiguration {
}
