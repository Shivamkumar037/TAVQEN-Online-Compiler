package com.compiler.backend.execution;

import com.compiler.backend.piston.PistonWebSocketClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Getter
public class ExecutionSession {

    private final String executionId;
    private final WebSocketSession frontendSession;
    private final String language;
    private final String version;
    private final String filename;
    private final Instant createdAt;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean active = new AtomicBoolean(true);

    @Setter
    private volatile PistonWebSocketClient pistonClient;

    @Setter
    private volatile ExecutionState state;

    @Setter
    private volatile Instant executionStartedAt;

    @Setter
    private volatile Instant lastActivityAt;

    @Setter
    private volatile ScheduledFuture<?> inactivityTimeoutTask;

    @Setter
    private volatile ScheduledFuture<?> maxExecutionTimeoutTask;

    public ExecutionSession(String executionId, WebSocketSession frontendSession,
                            String language, String version, String filename) {
        this.executionId = executionId;
        this.frontendSession = frontendSession;
        this.language = language;
        this.version = version;
        this.filename = filename;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.state = ExecutionState.INITIALIZING;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public void markCancelled() {
        cancelled.set(true);
        active.set(false);
        log.debug("[{}] Session marked as cancelled", executionId);
    }

    public void markCompleted() {
        active.set(false);
        log.debug("[{}] Session marked as completed", executionId);
    }

    public void updateLastActivity() {
        lastActivityAt = Instant.now();
    }

    public void cancelTimeoutTasks() {
        ScheduledFuture<?> inactivity = inactivityTimeoutTask;
        if (inactivity != null && !inactivity.isDone()) {
            inactivity.cancel(false);
            log.debug("[{}] Inactivity timeout task cancelled", executionId);
        }
        ScheduledFuture<?> maxExec = maxExecutionTimeoutTask;
        if (maxExec != null && !maxExec.isDone()) {
            maxExec.cancel(false);
            log.debug("[{}] Max execution timeout task cancelled", executionId);
        }
    }
}
