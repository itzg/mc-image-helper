package me.itzg.helpers.http;

import java.io.IOException;

public class ResponseParsingException extends IOException {

    public ResponseParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
