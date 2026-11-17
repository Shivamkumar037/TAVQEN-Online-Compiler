package com.compiler.backend.mock;

import com.compiler.backend.dto.ExecutionRequest;
import com.compiler.backend.dto.ExecutionResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Test client simulating the frontend WebSocket connection to /api/v1/execute.
 */
@Slf4j
@Getter
public class FrontendWebSocketTestClient implements WebSocket.Listener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<ExecutionResponse> receivedResponses = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> rawMessages = new BlockingQueueImpl<>();
    private final CompletableFuture<WebSocket> connectFuture = new CompletableFuture<>();
    private WebSocket webSocket;
    private final StringBuilder textBuffer = new StringBuilder();

    public static FrontendWebSocketTestClient connect(String url) throws Exception {
        FrontendWebSocketTestClient client = new FrontendWebSocketTestClient();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), client)
                .get(10, TimeUnit.SECONDS);

        client.webSocket = ws;
        return client;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("[FrontendTestClient] Connected to backend WebSocket");
        this.webSocket = webSocket;
        connectFuture.complete(webSocket);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            String message = textBuffer.toString();
            textBuffer.setLength(0);
            rawMessages.add(message);
            try {
                ExecutionResponse response = objectMapper.readValue(message, ExecutionResponse.class);
                receivedResponses.add(response);
                log.info("[FrontendTestClient] Received: {}", message);
            } catch (Exception e) {
                log.error("[FrontendTestClient] Failed to deserialize message: {}", message, e);
            }
        }
        webSocket.request(1);
        return null;
    }

    public void sendRequest(ExecutionRequest request) throws Exception {
        String json = objectMapper.writeValueAsString(request);
        log.info("[FrontendTestClient] Sending request: {}", json);
        webSocket.sendText(json, true).get(5, TimeUnit.SECONDS);
    }

    public ExecutionResponse waitForResponse(Duration timeout) throws InterruptedException {
        return receivedResponses.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ExecutionResponse waitForType(String expectedType, Duration timeout) throws Exception {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            ExecutionResponse resp = receivedResponses.poll(100, TimeUnit.MILLISECONDS);
            if (resp != null && expectedType.equalsIgnoreCase(resp.getType())) {
                return resp;
            }
        }
        throw new TimeoutException("Timed out waiting for response of type: " + expectedType);
    }

    public void close() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Test done");
        }
    }

    private static class BlockingQueueImpl<T> extends LinkedBlockingQueue<T> {}
}
