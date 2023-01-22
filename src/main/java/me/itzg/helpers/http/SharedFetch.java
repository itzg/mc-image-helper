package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
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
    private final Map<String, String> headers = new HashMap<>();
    @Getter
    final LatchingUrisInterceptor latchingUrisInterceptor = new LatchingUrisInterceptor();

    @Getter
    private final HttpClient reactiveClient;

    public SharedFetch(String forCommand) {
        final String userAgent = String.format("%s/%s (cmd=%s)",
            "mc-image-helper",
            McImageHelper.getVersion(),
            forCommand != null ? forCommand : "unspecified"
        );

        final String fetchSessionId = UUID.randomUUID().toString();

        reactiveClient = HttpClient.create()
            .headers(headers ->
                headers
                    .set(HttpHeaderNames.USER_AGENT.toString(), userAgent)
                    .set("x-fetch-session", fetchSessionId)
            );

        headers.put("x-fetch-session", fetchSessionId);
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
    public void close() {
    }
}
