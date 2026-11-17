package com.compiler.backend.execution;

import com.compiler.backend.config.CompilerProperties;
import com.compiler.backend.dto.ExecutionRequest;
import com.compiler.backend.dto.ExecutionResponse;
import com.compiler.backend.exception.ExecutionException;
import com.compiler.backend.piston.*;
import com.compiler.backend.websocket.WebSocketMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {
    private static final Set<String> SUPPORTED = Set.of("java", "python", "c", "cpp", "javascript", "go");
    private final ExecutionSessionManager sessions;
    private final CompilerProperties properties;
    private final ObjectMapper mapper;
    private final WebSocketMessageSender sender;
    private final ExecutionTimeoutManager timeouts;
    private final ExecutorService connector = Executors.newVirtualThreadPerTaskExecutor();

    public void start(WebSocketSession frontend, ExecutionRequest request) {
        validateStart(request);
        if (sessions.getSessionByFrontendSessionId(frontend.getId()) != null) throw new ExecutionException("An execution is already active on this WebSocket");
        String id = UUID.randomUUID().toString();
        ExecutionSession session = sessions.createSession(id, frontend, request.getLanguage().toLowerCase(), request.getVersion(), request.getFilename());
        status(session, "INITIALIZED", "Execution initialized");
        session.setState(ExecutionState.CONNECTING_TO_PISTON);
        status(session, "CONNECTING_TO_PISTON", "Connecting to execution engine");
        PistonWebSocketClient client = new PistonWebSocketClient(URI.create(properties.getPiston().getWebsocketUrl()), mapper, new Listener(session, request));
        session.setPistonClient(client);
        connector.submit(() -> {
            try { client.connectBlocking(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); fail(session, "Connection to Piston interrupted", ex); }
            catch (Exception ex) { fail(session, "Unable to connect to execution engine", ex); }
        });
    }
    public void input(WebSocketSession frontend, ExecutionRequest request) {
        ExecutionSession s = ownedActive(frontend, request.getExecutionId());
        if (request.getData() == null || request.getData().length() > 8192) throw new ExecutionException("Input must be between 1 and 8192 characters");
        if (s.getPistonClient() == null || !s.getPistonClient().isOpen()) throw new ExecutionException("Execution engine is not connected");
        s.updateLastActivity();
        s.getPistonClient().sendPacket(PistonMessage.builder().type("data").stream("stdin").data(request.getData()).build());
    }
    public void cancel(WebSocketSession frontend, String requestedId, String reason) {
        ExecutionSession s = ownedActive(frontend, requestedId);
        s.setState(ExecutionState.STOPPING); status(s, "STOPPING", reason);
        s.markCancelled();
        if (s.getPistonClient() != null) { s.getPistonClient().cancel(); s.getPistonClient().close(); }
        status(s, "CANCELLED", "Execution cancelled"); cleanup(s);
    }
    public void frontendDisconnected(WebSocketSession frontend) {
        ExecutionSession s = sessions.getSessionByFrontendSessionId(frontend.getId());
        if (s != null) { s.markCancelled(); if (s.getPistonClient() != null) { s.getPistonClient().cancel(); s.getPistonClient().close(); } cleanup(s); }
    }
    private void init(ExecutionSession s, ExecutionRequest r) {
        s.setState(ExecutionState.PISTON_CONNECTED); status(s, "PISTON_CONNECTED", "Execution engine connected");
        s.getPistonClient().sendPacket(PistonMessage.builder().type("init").language(s.getLanguage()).version(s.getVersion())
                .files(new PistonFileEntry[]{PistonFileEntry.builder().name(s.getFilename()).content(r.getCode()).build()})
                .compileTimeout(properties.getTimeout().getCompile()).runTimeout(Math.min(properties.getPiston().getRunTimeout(), properties.getTimeout().getMaximumExecution()))
                .compileCpuTime(properties.getTimeout().getCpuTime()).runCpuTime(properties.getTimeout().getCpuTime()).build());
        timeouts.schedule(s, () -> timeout(s, "Input/output inactivity timeout exceeded"), () -> timeout(s, "Maximum wall-clock execution time exceeded"));
    }
    private void event(ExecutionSession s, PistonMessage m) {
        if (!s.isActive() || s.isCancelled()) return;
        switch (m.getType()) {
            case "runtime" -> status(s, "INITIALIZED", "Runtime " + m.getLanguage() + " " + m.getVersion() + " initialized");
            case "stage" -> {
                if ("compile".equals(m.getStage())) { s.setState(ExecutionState.COMPILING); status(s, "COMPILING", "Compiling source"); }
                else if ("run".equals(m.getStage())) { s.setState(ExecutionState.RUNNING); s.setExecutionStartedAt(Instant.now()); status(s, "RUNNING", "Program running; stdin is available"); }
            }
            case "data" -> {
                if ("stdout".equals(m.getStream()) || "stderr".equals(m.getStream())) { s.updateLastActivity(); sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("OUTPUT").executionId(s.getExecutionId()).stream(m.getStream()).data(m.getData()).build()); }
            }
            case "exit" -> handleExit(s, m);
            default -> fail(s, "Unsupported message received from execution engine", null);
        }
    }
    private void handleExit(ExecutionSession s, PistonMessage m) {
        sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("EXIT").executionId(s.getExecutionId()).stage(m.getStage()).code(m.getCode()).signal(m.getSignal()).build());
        if ("compile".equals(m.getStage()) && Integer.valueOf(0).equals(m.getCode())) { s.setState(ExecutionState.COMPILE_SUCCESS); status(s, "COMPILE_SUCCESS", "Compilation completed"); return; }
        if (m.getCode() != null && m.getCode() != 0) { s.setState(ExecutionState.FAILED); status(s, "FAILED", "Execution failed"); }
        else { s.setState(ExecutionState.COMPLETED); status(s, "COMPLETED", "Execution completed"); }
        cleanup(s);
    }
    private void timeout(ExecutionSession s, String msg) { if (!s.isActive()) return; s.setState(ExecutionState.TIMEOUT); sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("TIMEOUT").executionId(s.getExecutionId()).message(msg).build()); sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("ERROR").executionId(s.getExecutionId()).message(msg).build()); if (s.getPistonClient()!=null) { s.getPistonClient().cancel(); s.getPistonClient().close(); } cleanup(s); }
    private void fail(ExecutionSession s, String message, Throwable cause) { if (!s.isActive()) return; log.warn("[{}] {}", s.getExecutionId(), message, cause); s.setState(ExecutionState.FAILED); sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("ERROR").executionId(s.getExecutionId()).message(message).build()); cleanup(s); }
    private void cleanup(ExecutionSession s) { s.markCompleted(); s.cancelTimeoutTasks(); sessions.removeSession(s.getExecutionId()); }
    private void status(ExecutionSession s, String status, String message) { sender.send(s.getFrontendSession(), ExecutionResponse.builder().type("STATUS").executionId(s.getExecutionId()).status(status).message(message).build()); }
    private ExecutionSession ownedActive(WebSocketSession frontend, String id) { ExecutionSession s = sessions.getSessionByFrontendSessionId(frontend.getId()); if (s == null || !s.isActive() || (id != null && !id.equals(s.getExecutionId()))) throw new ExecutionException("No active execution belongs to this WebSocket"); return s; }
    private void validateStart(ExecutionRequest r) { if (r.getLanguage()==null || !SUPPORTED.contains(r.getLanguage().toLowerCase())) throw new ExecutionException("Unsupported language"); if (blank(r.getVersion()) || blank(r.getFilename()) || blank(r.getCode())) throw new ExecutionException("language, version, filename, and code are required"); if (r.getCode().length() > properties.getSecurity().getMaxCodeSize()) throw new ExecutionException("Source code exceeds maximum size"); if (r.getFilename().contains("/") || r.getFilename().contains("\\")) throw new ExecutionException("Invalid filename"); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    @PreDestroy void destroy() { connector.shutdownNow(); }
    private final class Listener implements PistonEventListener { private final ExecutionSession s; private final ExecutionRequest r; private Listener(ExecutionSession s, ExecutionRequest r){this.s=s;this.r=r;} public void onOpen(){init(s,r);} public void onMessage(PistonMessage m){event(s,m);} public void onFailure(String m,Throwable t){fail(s,m,t);} public void onClosed(int c,String r,boolean remote){ if(s.isActive()&&!s.isCancelled()) fail(s,"Execution engine connection closed unexpectedly",null); } }
}
