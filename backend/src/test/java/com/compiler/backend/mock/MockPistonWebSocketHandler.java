package com.compiler.backend.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Mock implementation of Engineer-Man Piston WebSocket endpoint (/api/v2/connect)
 * for high-fidelity end-to-end integration testing.
 */
@Slf4j
@Component
public class MockPistonWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public enum Behavior {
        NORMAL_JAVA,
        COMPILATION_ERROR,
        RUNTIME_EXCEPTION,
        SCANNER_INPUT_SINGLE,
        SCANNER_INPUT_MULTIPLE,
        INACTIVITY_HANG,
        CUSTOM
    }

    private volatile Behavior currentBehavior = Behavior.NORMAL_JAVA;
    private volatile BiConsumer<WebSocketSession, JsonNode> customHandler;

    private final CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> inputStepPerSession = new ConcurrentHashMap<>();

    public void setBehavior(Behavior behavior) {
        this.currentBehavior = behavior;
        this.receivedMessages.clear();
        this.inputStepPerSession.clear();
        this.customHandler = null;
    }

    public void setCustomHandler(BiConsumer<WebSocketSession, JsonNode> customHandler) {
        this.currentBehavior = Behavior.CUSTOM;
        this.customHandler = customHandler;
        this.receivedMessages.clear();
        this.inputStepPerSession.clear();
    }

    public CopyOnWriteArrayList<String> getReceivedMessages() {
        return receivedMessages;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[MockPiston] Connection established from backend: {}", session.getId());
        inputStepPerSession.put(session.getId(), new AtomicInteger(0));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        receivedMessages.add(payload);
        log.info("[MockPiston] Received payload: {}", payload);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.has("type") ? root.get("type").asText() : "";

            if ("init".equalsIgnoreCase(type)) {
                handleInit(session, root);
            } else if ("data".equalsIgnoreCase(type)) {
                handleData(session, root);
            } else if ("signal".equalsIgnoreCase(type)) {
                handleSignal(session, root);
            }
        } catch (Exception e) {
            log.error("[MockPiston] Error processing message: {}", e.getMessage(), e);
        }
    }

    private void handleInit(WebSocketSession session, JsonNode root) throws IOException, InterruptedException {
        String language = root.has("language") ? root.get("language").asText() : "java";
        String version = root.has("version") ? root.get("version").asText() : "15.0.2";

        // Always send runtime message first
        sendJson(session, "{\"type\":\"runtime\",\"language\":\"" + language + "\",\"version\":\"" + version + "\"}");

        switch (currentBehavior) {
            case NORMAL_JAVA -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":0,\"signal\":null}");
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"run\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Hello World\\n\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"run\",\"code\":0,\"signal\":null}");
            }
            case COMPILATION_ERROR -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stderr\",\"data\":\"Main.java:3: error: cannot find symbol\\n\"}");
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":1,\"signal\":null}");
            }
            case RUNTIME_EXCEPTION -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":0,\"signal\":null}");
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"run\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stderr\",\"data\":\"Exception in thread \\\"main\\\" java.lang.ArithmeticException: / by zero\\n\"}");
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"run\",\"code\":1,\"signal\":null}");
            }
            case SCANNER_INPUT_SINGLE -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":0,\"signal\":null}");
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"run\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Enter your name:\\n\"}");
                // Wait for stdin
            }
            case SCANNER_INPUT_MULTIPLE -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":0,\"signal\":null}");
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"run\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Hello World\\nEnter your name:\\n\"}");
                // Wait for stdin 1
            }
            case INACTIVITY_HANG -> {
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"compile\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"compile\",\"code\":0,\"signal\":null}");
                sendJson(session, "{\"type\":\"stage\",\"stage\":\"run\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Waiting forever for input...\\n\"}");
                // Do nothing more, intentionally hang to test inactivity timeout
            }
            case CUSTOM -> {
                if (customHandler != null) {
                    customHandler.accept(session, root);
                }
            }
        }
    }

    private void handleData(WebSocketSession session, JsonNode root) throws IOException, InterruptedException {
        String stream = root.has("stream") ? root.get("stream").asText() : "";
        String data = root.has("data") ? root.get("data").asText() : "";

        if ("stdin".equalsIgnoreCase(stream)) {
            AtomicInteger step = inputStepPerSession.computeIfAbsent(session.getId(), k -> new AtomicInteger(0));
            int currentStep = step.incrementAndGet();

            if (currentBehavior == Behavior.SCANNER_INPUT_SINGLE) {
                // Program continues with the entered name
                String name = data.trim();
                sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Welcome " + name + "\\n\"}");
                Thread.sleep(50);
                sendJson(session, "{\"type\":\"exit\",\"stage\":\"run\",\"code\":0,\"signal\":null}");
            } else if (currentBehavior == Behavior.SCANNER_INPUT_MULTIPLE) {
                if (currentStep == 1) {
                    String name = data.trim();
                    sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Welcome " + name + "\\nEnter your age:\\n\"}");
                    // Waiting for step 2 (age)
                } else if (currentStep == 2) {
                    String age = data.trim();
                    sendJson(session, "{\"type\":\"data\",\"stream\":\"stdout\",\"data\":\"Your age is " + age + "\\n\"}");
                    Thread.sleep(50);
                    sendJson(session, "{\"type\":\"exit\",\"stage\":\"run\",\"code\":0,\"signal\":null}");
                }
            } else if (currentBehavior == Behavior.CUSTOM && customHandler != null) {
                customHandler.accept(session, root);
            }
        }
    }

    private void handleSignal(WebSocketSession session, JsonNode root) {
        String signal = root.has("signal") ? root.get("signal").asText() : "";
        log.info("[MockPiston] Received signal: {}", signal);
        try {
            sendJson(session, "{\"type\":\"exit\",\"stage\":\"run\",\"code\":null,\"signal\":\"" + signal + "\"}");
            session.close();
        } catch (Exception e) {
            log.error("[MockPiston] Error handling signal: {}", e.getMessage());
        }
    }

    private synchronized void sendJson(WebSocketSession session, String json) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[MockPiston] Connection closed: {}", session.getId());
        inputStepPerSession.remove(session.getId());
    }
}
