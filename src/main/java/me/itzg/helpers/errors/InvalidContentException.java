package me.itzg.helpers.errors;

import java.io.IOException;

public class InvalidContentException extends IOException {

    public InvalidContentException(String message) {
        super(message);
    }
    public InvalidContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
