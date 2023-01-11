package me.itzg.helpers.http;

import java.net.URI;
import lombok.Getter;
import org.apache.hc.client5.http.HttpResponseException;

public class FailedRequestException extends RuntimeException {

    @Getter
    private final URI uri;
    @Getter
    private final int statusCode;

    public FailedRequestException(HttpResponseException e, URI uri) {
        super(
            String.format("HTTP request failed uri: %s %s", uri, e.getMessage())
        );
        this.uri = uri;
        this.statusCode = e.getStatusCode();
    }
}
