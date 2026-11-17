package com.compiler.backend.piston;

public interface PistonEventListener {
    void onOpen();
    void onMessage(PistonMessage message);
    void onFailure(String message, Throwable cause);
    void onClosed(int code, String reason, boolean remote);
}
