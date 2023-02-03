package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import lombok.Getter;

public class FailedRequestException extends RuntimeException {

    @Getter
    private final URI uri;
    @Getter
    private final int statusCode;

    /**
     * Reactor Netty flavor
     */
    public FailedRequestException(HttpResponseStatus status, URI uri, String msg) {
        super(
            String.format("HTTP request of %s failed with %s: %s", uri, status, msg)
        );
        this.uri = uri;
        this.statusCode = status.code();
    }

    @SuppressWarnings("unused")
    public static boolean isNotFound(Throwable throwable) {
        return isStatus(throwable, HttpResponseStatus.NOT_FOUND);
    }

    public static boolean isBadRequest(Throwable throwable) {
        return isStatus(throwable, HttpResponseStatus.BAD_REQUEST);
    }

    private static boolean isStatus(Throwable throwable, HttpResponseStatus status) {
        if (throwable instanceof FailedRequestException) {
            return ((FailedRequestException) throwable).getStatusCode() == status.code();
        }
        return false;
    }
}
