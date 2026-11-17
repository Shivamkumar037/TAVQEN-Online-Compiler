package com.compiler.backend.websocket;

import com.compiler.backend.dto.ExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketMessageSender {
    private final ObjectMapper mapper;
    public void send(WebSocketSession session, ExecutionResponse response) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) { if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(response))); }
        } catch (IOException ex) { log.debug("Could not send response to frontend {}", session.getId(), ex); }
    }
}
