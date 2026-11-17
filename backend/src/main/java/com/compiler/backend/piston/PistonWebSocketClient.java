package com.compiler.backend.piston;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

@Slf4j
public final class PistonWebSocketClient extends WebSocketClient {
    private final ObjectMapper mapper;
    private final PistonEventListener listener;

    public PistonWebSocketClient(URI uri, ObjectMapper mapper, PistonEventListener listener) {
        super(uri);
        this.mapper = mapper;
        this.listener = listener;
    }
    @Override public void onOpen(ServerHandshake ignored) { listener.onOpen(); }
    @Override public void onMessage(String payload) {
        try { listener.onMessage(mapper.readValue(payload, PistonMessage.class)); }
        catch (Exception ex) { listener.onFailure("Invalid message received from Piston", ex); }
    }
    @Override public void onClose(int code, String reason, boolean remote) { listener.onClosed(code, reason, remote); }
    @Override public void onError(Exception ex) { listener.onFailure("Piston WebSocket error", ex); }
    public void sendPacket(PistonMessage packet) {
        try { send(mapper.writeValueAsString(packet)); }
        catch (Exception ex) { listener.onFailure("Unable to serialize Piston packet", ex); }
    }
    public void cancel() {
        if (isOpen()) sendPacket(PistonMessage.builder().type("signal").signal("SIGKILL").build());
    }
}
