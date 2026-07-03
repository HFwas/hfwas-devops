package com.hfwas.devops.user.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.user.context.AnonymousUserAccessor;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.security.JwtAuthFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ComponentScan(basePackages = "com.hfwas.devops.user")
@MapperScan(value = "com.hfwas.devops.user.mapper", markerInterface = BaseMapper.class)
@EnableConfigurationProperties(UserJwtProperties.class)
public class UserCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(CurrentUserAccessor.class)
    public CurrentUserAccessor anonymousCurrentUserAccessor() {
        return AnonymousUserAccessor.INSTANCE;
    }
}
