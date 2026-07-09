package com.hfwas.devops.user.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.user.context.AnonymousUserAccessor;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.message.spi.NoOpSiteMessagePublisher;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import com.hfwas.devops.user.message.spi.NoOpExternalNotifyPublisher;
import com.hfwas.devops.user.message.spi.ExternalNotifyPublisher;
import com.hfwas.devops.user.operlog.spi.NoOpOperLogRecorder;
import com.hfwas.devops.user.operlog.spi.OperLogRecorder;
import com.hfwas.devops.user.security.DefaultTenantAccessValidator;
import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import com.hfwas.devops.user.spi.NoOpUserDisplayNameResolver;
import com.hfwas.devops.user.spi.NoOpUserIdentityResolver;
import com.hfwas.devops.user.spi.UserDisplayNameResolver;
import com.hfwas.devops.user.spi.UserIdentityResolver;
import com.hfwas.devops.user.spi.TenantAccessValidator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableAspectJAutoProxy
@EnableAsync
@ComponentScan(basePackages = "com.hfwas.devops.user")
@MapperScan(value = {
        "com.hfwas.devops.user.mapper",
        "com.hfwas.devops.user.operlog.mapper"
}, markerInterface = BaseMapper.class)
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

    @Bean
    @ConditionalOnMissingBean(SiteMessagePublisher.class)
    public SiteMessagePublisher noOpSiteMessagePublisher() {
        return NoOpSiteMessagePublisher.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(ExternalNotifyPublisher.class)
    public ExternalNotifyPublisher noOpExternalNotifyPublisher() {
        return NoOpExternalNotifyPublisher.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(OperLogRecorder.class)
    public OperLogRecorder noOpOperLogRecorder() {
        return NoOpOperLogRecorder.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(UserDisplayNameResolver.class)
    public UserDisplayNameResolver noOpUserDisplayNameResolver() {
        return NoOpUserDisplayNameResolver.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(UserIdentityResolver.class)
    public UserIdentityResolver noOpUserIdentityResolver() {
        return NoOpUserIdentityResolver.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(TenantAccessValidator.class)
    public TenantAccessValidator tenantAccessValidator(TenantService tenantService,
            TenantMemberService tenantMemberService) {
        return new DefaultTenantAccessValidator(tenantService, tenantMemberService);
    }
}
