package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import lombok.Getter;
import org.apache.hc.client5.http.HttpResponseException;

public class FailedRequestException extends RuntimeException {

    @Getter
    private final URI uri;
    @Getter
    private final int statusCode;

    /**
     * Apache HTTP Client flavor
     */
    public FailedRequestException(HttpResponseException e, URI uri) {
        super(
            String.format("HTTP request of %s failed with %d: %s", uri, e.getStatusCode(), e.getMessage())
        );
        this.uri = uri;
        this.statusCode = e.getStatusCode();
    }

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
}
