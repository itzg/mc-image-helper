package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString
public class FailedRequestException extends RuntimeException {

    private final URI uri;
    private final int statusCode;
    private final String body;

    /**
     * Reactor Netty flavor
     */
    public FailedRequestException(HttpResponseStatus status, URI uri, String body, String msg) {
        super(
            String.format("HTTP request of %s failed with %s: %s", uri, status, msg)
        );
        this.uri = uri;
        this.statusCode = status.code();
        this.body = body;
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
