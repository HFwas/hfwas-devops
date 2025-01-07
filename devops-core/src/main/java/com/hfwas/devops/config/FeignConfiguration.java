package com.hfwas.devops.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/1/6
 */
@Configuration
public class FeignConfiguration implements RequestInterceptor {

    @Value("${devops.nexus.username}")
    private String nexusUsername;
    @Value("${devops.nexus.password}")
    private String nexusPassword;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 定时任务触发
        if (servletRequestAttributes == null) {
            servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        }

        String url = requestTemplate.url();
        if (url.startsWith("/service/rest")) {
            String format = String.format("%s:%s", nexusUsername, nexusPassword);
            String s = Base64.getEncoder().encodeToString(format.getBytes(StandardCharsets.UTF_8));
            String formatted = String.format("Basic %s", s);
            requestTemplate.header("Authorization", formatted);
        }
    }


}
