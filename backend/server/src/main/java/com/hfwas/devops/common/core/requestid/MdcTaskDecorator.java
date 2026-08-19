package com.hfwas.devops.common.core.requestid;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 上下文传递装饰器。
 * 用于 {@link org.springframework.scheduling.annotation.Async @Async} 或线程池，
 * 将父线程的 MDC（含 requestId）传递到子线程，确保异步操作日志也能携带 requestId。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Bean
 * public Executor taskExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new MdcTaskDecorator());
 *     executor.setCorePoolSize(4);
 *     executor.setMaxPoolSize(8);
 *     executor.setQueueCapacity(100);
 *     executor.setThreadNamePrefix("mdc-async-");
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null && !contextMap.isEmpty()) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                if (previous != null && !previous.isEmpty()) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}