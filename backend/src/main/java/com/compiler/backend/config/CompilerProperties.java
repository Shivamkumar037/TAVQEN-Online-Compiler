package com.compiler.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "compiler")
public class CompilerProperties {

    private Piston piston = new Piston();
    private Timeout timeout = new Timeout();
    private Security security = new Security();

    @Getter @Setter
    public static class Piston {
        private String websocketUrl = "ws://localhost:2004/api/v2/connect";
        /** Must not exceed the Piston container's PISTON_RUN_TIMEOUT. */
        private long runTimeout = 600000;
    }

    @Getter @Setter
    public static class Timeout {
        private long compile = 10000;
        private long inputInactivity = 300000;
        private long maximumExecution = 600000;
        private long cpuTime = 10000;
    }

    @Getter @Setter
    public static class Security {
        private int maxCodeSize = 65536;
        private int maxConcurrentSessions = 50;
        private String allowedOrigins = "*";
    }
}
