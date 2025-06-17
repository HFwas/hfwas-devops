package com.hfwas.devops.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.ServletRequestHandledEvent;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/1/31
 */
@Component
@Slf4j
public class RequestTimingEventListener implements ApplicationListener<ServletRequestHandledEvent> {
    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        Throwable failureCause = event.getFailureCause();
        String failureCauseMessage = (failureCause == null) ? "" : failureCause.getMessage();

        String clientAddress = event.getClientAddress();
        String requestUrl = event.getRequestUrl();
        String method = event.getMethod();
        int statusCode = event.getStatusCode();
        long processingTimeMillis = event.getProcessingTimeMillis();
        if (failureCause == null) {
            log.info("客户端地址：{}, 状态码：{}，请求路径：{}，请求方法：{}，处理耗时：{} ms", clientAddress, statusCode, requestUrl, method, processingTimeMillis);
        } else {
            log.error("客户端地址：{}，状态码：{}，请求路径：{}，请求方法：{}，处理耗时：{} ms，错误信息：{}", clientAddress, statusCode, requestUrl, method, processingTimeMillis, failureCauseMessage);
        }
    }
}
