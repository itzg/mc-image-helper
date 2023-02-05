package me.itzg.helpers.env;

public class InterpolationException extends RuntimeException {

    public InterpolationException(String msg, Throwable e) {
        super(msg, e);
    }
}
