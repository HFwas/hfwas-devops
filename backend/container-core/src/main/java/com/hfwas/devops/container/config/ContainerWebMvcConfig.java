package com.hfwas.devops.container.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ContainerWebMvcConfig implements WebMvcConfigurer {

    private final ArgoAdminInterceptor argoAdminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(argoAdminInterceptor).addPathPatterns("/argo/**");
    }
}
