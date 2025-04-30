package me.itzg.helpers.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.GenericException;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Provides an efficient way to make multiple web requests since a single client
 * is shared.
 * <p>
 *     <b>NOTE:</b> {@link FetchBuilderBase} makes use of this class to abstract
 *     away the need to know about one-off requests vs shared requests.
 * </p>
 */
@Getter
@Slf4j
public class SharedFetch implements AutoCloseable {

    private final Map<String, String> headers = new HashMap<>();
    final LatchingUrisInterceptor latchingUrisInterceptor = new LatchingUrisInterceptor();

    private final HttpClient reactiveClient;

    public SharedFetch(String forCommand, Options options) {
        final String userAgent = String.format("%s/%s/%s (cmd=%s)",
            "itzg",
            "mc-image-helper",
            McImageHelper.getVersion(),
            forCommand != null ? forCommand : "unspecified"
        );

        final String fetchSessionId = UUID.randomUUID().toString();

        final ConnectionProvider.Builder connectionProviderBuilder = ConnectionProvider.create("custom")
            .mutate();
        if (connectionProviderBuilder == null) {
            throw new GenericException("Unable to mutate default connection provider");
        }

        final ConnectionProvider connectionProvider = connectionProviderBuilder
            .maxIdleTime(options.getMaxIdleTimeout())
            .pendingAcquireTimeout(options.getPendingAcquireTimeout())
            .build();

        reactiveClient = HttpClient.create(connectionProvider)
            .proxyWithSystemProperties()
            .headers(headers -> {
                    headers
                        .set(HttpHeaderNames.USER_AGENT.toString(), userAgent)
                        .set("x-fetch-session", fetchSessionId);
                    if (options.getExtraHeaders() != null) {
                        options.getExtraHeaders().forEach(headers::set);
                    }
                }
            )
            // Reference https://projectreactor.io/docs/netty/release/reference/index.html#response-timeout
            .responseTimeout(options.getResponseTimeout())
            // Reference https://projectreactor.io/docs/netty/release/reference/index.html#ssl-tls-timeout
            .secure(spec ->
                spec.sslContext(Http11SslContextSpec.forClient())
                .handshakeTimeout(options.getTlsHandshakeTimeout())
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

    @Builder
    @Getter
    public static class Options {

        public static final Duration DEFAULT_MAX_IDLE_TIMEOUT = Duration.ofSeconds(30);

        @Default
        private final Duration responseTimeout
            // not set by default
            = Duration.ofSeconds(5);

        @Default
        private final Duration tlsHandshakeTimeout
            // double the Netty default
            = Duration.ofSeconds(20);

        @Default
        private final Duration maxIdleTimeout
            = DEFAULT_MAX_IDLE_TIMEOUT;

        /**
         * Increased default from Netty's {@link ConnectionProvider#DEFAULT_POOL_ACQUIRE_TIMEOUT}
         */
        @Default
        private final Duration pendingAcquireTimeout = Duration.ofSeconds(120);

        private final Map<String,String> extraHeaders;

        public Options withHeader(String key, String value) {
            final Map<String, String> newHeaders = extraHeaders != null ?
                new HashMap<>(extraHeaders) : new HashMap<>();
            newHeaders.put(key, value);

            return new Options(
                responseTimeout, tlsHandshakeTimeout, maxIdleTimeout, pendingAcquireTimeout,
                newHeaders
            );
        }
    }
}
