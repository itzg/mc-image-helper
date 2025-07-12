package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString
public class FailedRequestException extends RuntimeException {

    private final URI uri;
    private final int statusCode;
    private final String body;
    private final HttpHeaders headers;

    /**
     * Reactor Netty flavor
     */
    public FailedRequestException(HttpResponseStatus status, URI uri, String body, String msg, HttpHeaders headers) {
        super(
            String.format("HTTP request of %s failed with %s: %s", obfuscate(uri), status, msg)
        );
        this.uri = uri;
        this.statusCode = status.code();
        this.body = body;
        this.headers = headers;
    }

    public static String obfuscate(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo() != null ? "*:*" : null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
                .toString();
        } catch (URISyntaxException e) {
            return "???";
        }
    }

    @SuppressWarnings("unused")
    public static boolean isNotFound(Throwable throwable) {
        return isStatus(throwable, HttpResponseStatus.NOT_FOUND);
    }

    public static boolean isForbidden(Throwable throwable) {
        return isStatus(throwable, HttpResponseStatus.FORBIDDEN);
    }

    public static boolean isStatus(Throwable throwable, HttpResponseStatus... statuses) {
        if (throwable instanceof FailedRequestException) {
            final int actualStatus = ((FailedRequestException) throwable).getStatusCode();
            for (final HttpResponseStatus status : statuses) {
                if (status.code() == actualStatus) {
                    return true;
                }
            }
        }
        return false;
    }
}
