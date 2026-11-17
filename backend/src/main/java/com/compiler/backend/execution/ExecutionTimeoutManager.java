package com.compiler.backend.execution;

import com.compiler.backend.config.CompilerProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ExecutionTimeoutManager {
    private final CompilerProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public void schedule(ExecutionSession session, Runnable inactivity, Runnable maximum) {
        session.setInactivityTimeoutTask(scheduler.scheduleWithFixedDelay(() -> {
            if (session.isActive() && System.currentTimeMillis() - session.getLastActivityAt().toEpochMilli() >= properties.getTimeout().getInputInactivity()) inactivity.run();
        }, properties.getTimeout().getInputInactivity(), 1000, TimeUnit.MILLISECONDS));
        session.setMaxExecutionTimeoutTask(scheduler.schedule(maximum, properties.getTimeout().getMaximumExecution(), TimeUnit.MILLISECONDS));
    }
    @PreDestroy void shutdown() { scheduler.shutdownNow(); }
}
