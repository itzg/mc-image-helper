package me.itzg.helpers.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2SettingsFrame;
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
import org.jspecify.annotations.NonNull;
import reactor.netty.ChannelPipelineConfigurer;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.SslProvider.GenericSslContextSpec;

/**
 * Provides an efficient way to make multiple web requests since a single client is shared.
 * <p>
 * <b>NOTE:</b> {@link FetchBuilderBase} makes use of this class to abstract
 * away the need to know about one-off requests vs shared requests.
 * </p>
 */
@Getter
@Slf4j
public class SharedFetch implements AutoCloseable {

    private final Map<String, String> headers = new HashMap<>();
    final LatchingUrisInterceptor latchingUrisInterceptor = new LatchingUrisInterceptor();

    private final HttpClient reactiveClient;

    private final URI filesViaUrl;

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

        reactiveClient =
            applyWiretap(
                applyHttp2Option(
                    HttpClient.create(connectionProvider)
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
                        .responseTimeout(options.getResponseTimeout()),
                    options
                ),
                options
            );

        headers.put("x-fetch-session", fetchSessionId);

        this.filesViaUrl = options.getFilesViaUrl();
    }

    private HttpClient applyWiretap(HttpClient c, Options options) {
        return options.isWiretap() ? c.wiretap(true) : c;
    }

    private HttpClient applyHttp2Option(HttpClient c, Options options) {
        if (options.isUseHttp2()) {
            log.debug("Using HTTP/2");
            // https://projectreactor.io/docs/netty/release/reference/http-client.html#HTTP2
            return c
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
                .doOnChannelInit(ensureHttpSettingsFlush())
                // ignored for HTTP/1.1
                .http2Settings(settings ->
                    // Reference https://projectreactor.io/docs/netty/release/reference/index.html#http2-settings
                    settings
                        .initialWindowSize(options.getHttp2InitialWindowSize())
                        .maxFrameSize(options.getHttp2MaxFrameSize())
                )
                .secure(spec -> applySslContext(options, spec));

        }
        else {
            log.debug("Using HTTP/1.1");
            return c
                .protocol(HttpProtocol.HTTP11)
                .secure(spec -> applySslContext(options, spec));
        }
    }

    /**
     * Some HTTP/2 servers (e.g. Cloudflare) will not send the initial settings frame until the first request is sent.
     * This can cause a delay in the first request since the client will wait for the settings frame to be received before sending the request.
     * By adding a channel handler that flushes the channel after the settings frame is sent, we can ensure that the settings frame is sent immediately and the first request is not delayed.
     */
    private static @NonNull ChannelPipelineConfigurer ensureHttpSettingsFlush() {
        return (connectionObserver, channel, remoteAddress) -> {
            channel.pipeline().addFirst("immediate-h2-flush", new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        super.write(ctx, msg, promise);
                        // If we just sent the H2 connection settings, force an immediate socket flush
                        if (msg instanceof Http2SettingsFrame) {
                            ctx.flush();
                        }
                    }
                }
            );
        };
    }

    private static void applySslContext(Options options, SslProvider.SslContextSpec spec) {
        spec.sslContext((GenericSslContextSpec<?>) (
                options.isUseHttp2() ?
                    Http2SslContextSpec.forClient()
                    : Http11SslContextSpec.forClient()
            ))
            // Reference https://projectreactor.io/docs/netty/release/reference/index.html#ssl-tls-timeout
            .handshakeTimeout(options.getTlsHandshakeTimeout());
    }

    public FetchBuilderBase<?> fetch(URI uri) {
        return new FetchBuilderBase<>(uri, this);
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

        private final Map<String, String> extraHeaders;

        /**
         * Can be set for unit testing file downloads where the original URL's path is resolved against this given URL.
         */
        private final URI filesViaUrl;

        @Default
        private final boolean useHttp2 = true;

        @Default
        private final int http2InitialWindowSize = 65535 * 16;

        @Default
        private final int http2MaxFrameSize = 65535;

        private final boolean wiretap;

        public Options withHeader(String key, String value) {
            final Map<String, String> newHeaders = extraHeaders != null ?
                new HashMap<>(extraHeaders) : new HashMap<>();
            newHeaders.put(key, value);

            return new Options(
                responseTimeout, tlsHandshakeTimeout, maxIdleTimeout, pendingAcquireTimeout,
                newHeaders, filesViaUrl, useHttp2, http2InitialWindowSize, http2MaxFrameSize, wiretap
            );
        }
    }
}
