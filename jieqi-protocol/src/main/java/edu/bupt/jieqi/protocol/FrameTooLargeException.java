package edu.bupt.jieqi.protocol;

public final class FrameTooLargeException extends IllegalArgumentException {
    public FrameTooLargeException(int byteLength) {
        super("WebSocket text frame must be smaller than 1024 bytes; got " + byteLength);
    }
}

