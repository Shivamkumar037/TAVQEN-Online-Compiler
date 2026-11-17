package com.compiler.backend.websocket;

import com.compiler.backend.dto.ExecutionRequest;
import com.compiler.backend.dto.ExecutionResponse;
import com.compiler.backend.execution.ExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper; private final ExecutionService executionService; private final WebSocketMessageSender sender;
    @Override public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try { ExecutionRequest r=mapper.readValue(message.getPayload(), ExecutionRequest.class); if(r.getType()==null) throw new IllegalArgumentException("type is required"); switch(r.getType().toUpperCase()) { case "START" -> executionService.start(session,r); case "INPUT" -> executionService.input(session,r); case "CANCEL" -> executionService.cancel(session,r.getExecutionId(),"Cancellation requested by user"); default -> throw new IllegalArgumentException("Unsupported message type"); } }
        catch (Exception ex) { sender.send(session, ExecutionResponse.builder().type("ERROR").message(ex.getMessage()==null?"Invalid request":ex.getMessage()).build()); }
    }
    @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus status){ executionService.frontendDisconnected(s); }
    @Override public void handleTransportError(WebSocketSession s, Throwable ex){ executionService.frontendDisconnected(s); }
}
