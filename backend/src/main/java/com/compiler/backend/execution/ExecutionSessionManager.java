package com.compiler.backend.execution;

import com.compiler.backend.config.CompilerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSessionManager {

    private final ConcurrentHashMap<String, ExecutionSession> sessions = new ConcurrentHashMap<>();
    // secondary index: frontendSessionId -> executionId
    private final ConcurrentHashMap<String, String> frontendSessionIndex = new ConcurrentHashMap<>();
    private final CompilerProperties compilerProperties;

    public ExecutionSession createSession(String executionId, WebSocketSession frontendSession,
                                          String language, String version, String filename) {
        int maxSessions = compilerProperties.getSecurity().getMaxConcurrentSessions();
        if (sessions.size() >= maxSessions) {
            throw new com.compiler.backend.exception.ExecutionException(
                    "Maximum concurrent execution sessions reached: " + maxSessions);
        }

        ExecutionSession session = new ExecutionSession(executionId, frontendSession, language, version, filename);
        sessions.put(executionId, session);
        frontendSessionIndex.put(frontendSession.getId(), executionId);
        log.info("[{}] Session created for frontend session {}, language={}, version={}",
                executionId, frontendSession.getId(), language, version);
        return session;
    }

    public ExecutionSession getSession(String executionId) {
        return executionId != null ? sessions.get(executionId) : null;
    }

    public ExecutionSession getSessionByFrontendSessionId(String frontendSessionId) {
        String executionId = frontendSessionIndex.get(frontendSessionId);
        return executionId != null ? sessions.get(executionId) : null;
    }

    public void removeSession(String executionId) {
        ExecutionSession session = sessions.remove(executionId);
        if (session != null) {
            frontendSessionIndex.remove(session.getFrontendSession().getId());
            log.info("[{}] Session removed. Active sessions: {}", executionId, sessions.size());
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public Collection<ExecutionSession> getAllSessions() {
        return sessions.values();
    }
}
