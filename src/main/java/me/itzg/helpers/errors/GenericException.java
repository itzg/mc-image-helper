package me.itzg.helpers.errors;

public class GenericException extends RuntimeException {

    public GenericException(String message) {
        super(message);
    }

    public static GenericException formatted(String format, Object... args) {
        return new GenericException(String.format(format, args));
    }

    public GenericException(String message, Throwable cause) {
        super(message+": "+cause.getMessage(), cause);
    }
}
