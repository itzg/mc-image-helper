package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.get.ExtendedRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import reactor.netty.http.client.HttpClient;

/**
 * Provides an efficient way to make multiple web requests since a single client
 * is shared.
 * <p>
 *     <b>NOTE:</b> {@link FetchBuilderBase} makes use of this class to abstract
 *     away the need to know about one-off requests vs shared requests.
 * </p>
 */
@Slf4j
public class SharedFetch implements AutoCloseable {

    @Getter
    private final CloseableHttpClient client;

    @Getter
    private final Map<String, String> headers = new HashMap<>();
    @Getter
    final LatchingUrisInterceptor latchingUrisInterceptor = new LatchingUrisInterceptor();

    @Getter
    private final HttpClient reactiveClient;

    public SharedFetch(String forCommand) {
        this(forCommand, 5, 2);
    }

    public SharedFetch(String forCommand, int retryCount, int retryDelaySeconds) {
        final String userAgent = String.format("%s/%s (cmd=%s)",
            "mc-image-helper",
            McImageHelper.getVersion(),
            forCommand != null ? forCommand : "unspecified"
        );

        reactiveClient = HttpClient.create()
            .headers(headers ->
                headers.set(HttpHeaderNames.USER_AGENT.toString(), userAgent)
            );

        this.client = HttpClients.custom()
            .addRequestInterceptorFirst((request, entity, context) -> {
                try {
                    log.debug("Request: {} {} with headers {}",
                        request.getMethod(), request.getUri(), Arrays.toString(request.getHeaders()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            })
            .addExecInterceptorFirst("latchingUris", latchingUrisInterceptor)
            .useSystemProperties()
            .setUserAgent(userAgent)
            .setRetryStrategy(
                new ExtendedRequestRetryStrategy(retryCount, retryDelaySeconds)
            )
            .build();

        headers.put("x-fetch-session", UUID.randomUUID().toString());
    }

    public FetchBuilderBase<?> fetch(URI uri) {
        return new FetchBuilderBase<>(uri, this);
    }

    @SuppressWarnings("unused")
    public SharedFetch addHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
