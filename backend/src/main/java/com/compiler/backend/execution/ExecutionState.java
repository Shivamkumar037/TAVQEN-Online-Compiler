package com.compiler.backend.execution;

public enum ExecutionState {
    INITIALIZING,
    CONNECTING_TO_PISTON,
    PISTON_CONNECTED,
    COMPILING,
    COMPILE_SUCCESS,
    RUNNING,
    WAITING_FOR_INPUT,
    STOPPING,
    COMPLETED,
    TIMEOUT,
    FAILED,
    CANCELLED
}
