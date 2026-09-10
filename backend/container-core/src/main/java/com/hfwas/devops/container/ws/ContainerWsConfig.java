package com.hfwas.devops.container.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ContainerWsConfig implements WebSocketConfigurer {

    private final ContainerWsAuthInterceptor authInterceptor;
    private final ResourceWatchHandler resourceWatchHandler;
    private final LogTailHandler logTailHandler;

    public ContainerWsConfig(ContainerWsAuthInterceptor authInterceptor,
                             ResourceWatchHandler resourceWatchHandler,
                             LogTailHandler logTailHandler) {
        this.authInterceptor = authInterceptor;
        this.resourceWatchHandler = resourceWatchHandler;
        this.logTailHandler = logTailHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(resourceWatchHandler, "/ws/container/watch/**")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");

        registry.addHandler(logTailHandler, "/ws/container/logs/**")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}