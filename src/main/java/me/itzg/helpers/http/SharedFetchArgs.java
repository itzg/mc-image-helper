package me.itzg.helpers.http;

import java.time.Duration;
import me.itzg.helpers.http.SharedFetch.Options;
import picocli.CommandLine.Option;

/**
 * Usage:
 * <pre>
 * {@code
 *     @ArgGroup(exclusive = false)
 *     SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();
 * }
 * </pre>
 */
public class SharedFetchArgs {

    private final Options.OptionsBuilder optionsBuilder = Options.builder();

    @Option(names = "--http-response-timeout", defaultValue = "${env:FETCH_RESPONSE_TIMEOUT:-PT30S}",
        paramLabel = "DURATION",
        description = "The response timeout to apply to HTTP operations. Parsed from ISO-8601 format. "
            + "Default: ${DEFAULT-VALUE}"
    )
    public void setResponseTimeout(Duration timeout) {
        optionsBuilder.responseTimeout(timeout);
    }

    @Option(names = "--tls-handshake-timeout", defaultValue = "${env:FETCH_TLS_HANDSHAKE_TIMEOUT:-PT30S}",
        paramLabel = "DURATION",
        description = "Default: ${DEFAULT-VALUE}"
    )
    public void setTlsHandshakeTimeout(Duration timeout) {
        optionsBuilder.tlsHandshakeTimeout(timeout);
    }

    @Option(names = "--connection-pool-max-idle-timeout", defaultValue = "${env:FETCH_CONNECTION_POOL_MAX_IDLE_TIMEOUT:-PT15S}",
        paramLabel = "DURATION"
    )
    public void setConnectionPoolMaxIdleTimeout(Duration timeout) {
        optionsBuilder.maxIdleTimeout(timeout);
    }

    @Option(names = "--connection-pool-pending-acquire-timeout", defaultValue = "${env:FETCH_CONNECTION_POOL_PENDING_ACQUIRE_TIMEOUT}",
        paramLabel = "DURATION"
    )
    public void setPendingAcquireTimeout(Duration timeout) {
        optionsBuilder.pendingAcquireTimeout(timeout);
    }

    @Option(names = "--use-http2", defaultValue = "${env:FETCH_USE_HTTP2:-true}",
        description = "Whether to use HTTP/2. Default: ${DEFAULT-VALUE}"
    )
    public void setUseHttp2(boolean useHttp2) {
        optionsBuilder.useHttp2(useHttp2);
    }

    @Option(names = "--wiretap", defaultValue = "${env:FETCH_WIRETAP:-false}",
        description = "Whether to enable Reactor Netty wiretap logging. Default: ${DEFAULT-VALUE}"
    )
    public void setWiretap(boolean wiretap) {
        optionsBuilder.wiretap(wiretap);
    }

    public Options options() {
        return optionsBuilder.build();
    }
}
