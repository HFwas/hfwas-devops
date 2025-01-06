package com.hfwas.devops.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/1/6
 */
@Configuration
public class FeignConfiguration implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 定时任务触发
        if (servletRequestAttributes == null) {
            servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        }
    }
}
