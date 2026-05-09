package com.example.messagereader.api;

public class MessageReaderException extends RuntimeException {
    public MessageReaderException(String message) {
        super(message);
    }

    public MessageReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
