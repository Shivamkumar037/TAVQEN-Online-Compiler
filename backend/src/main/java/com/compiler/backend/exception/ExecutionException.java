package com.compiler.backend.exception;

public class ExecutionException extends RuntimeException {
    private final String executionId;

    public ExecutionException(String message) {
        super(message);
        this.executionId = null;
    }

    public ExecutionException(String message, String executionId) {
        super(message);
        this.executionId = executionId;
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.executionId = null;
    }

    public String getExecutionId() {
        return executionId;
    }
}
