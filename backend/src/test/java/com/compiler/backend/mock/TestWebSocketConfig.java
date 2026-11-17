package com.compiler.backend.mock;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Test configuration registering the mock Piston WebSocket handler.
 */
@TestConfiguration
@EnableWebSocket
@RequiredArgsConstructor
public class TestWebSocketConfig implements WebSocketConfigurer {

    private final MockPistonWebSocketHandler mockPistonWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mockPistonWebSocketHandler, "/mock/piston/api/v2/connect")
                .setAllowedOrigins("*");
    }
}
