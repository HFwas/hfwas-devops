package com.hfwas.devops.ws;

import com.hfwas.devops.common.core.base.BaseResult;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PodExecWebSocketConfig implements WebSocketConfigurer {

    private final PodExecAuthHandshakeInterceptor handshakeInterceptor;
    private final PodExecWebSocketHandler webSocketHandler;

    public PodExecWebSocketConfig(PodExecAuthHandshakeInterceptor handshakeInterceptor,
                                  PodExecWebSocketHandler webSocketHandler) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/exec/{pipelineId}/{runId}/{jobId}")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}