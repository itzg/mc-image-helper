package me.itzg.helpers.http;

import java.time.Duration;
import me.itzg.helpers.http.SharedFetch.Options;
import picocli.CommandLine.Option;

public class SharedFetchArgs {

    private final Options.OptionsBuilder optionsBuilder = Options.builder();

    @Option(names = "--http-response-timeout", defaultValue = "${env:FETCH_RESPONSE_TIMEOUT}",
        description = "The response timeout to apply to HTTP operations. Parsed from ISO-8601 format."
    )
    public void setResponseTimeout(Duration timeout) {
        optionsBuilder.responseTimeout(timeout);
    }

    @Option(names = "--tls-handshake-timeout", defaultValue = "${env:FETCH_TLS_HANDSHAKE_TIMEOUT}")
    public void setTlsHandshakeTimeout(Duration timeout) {
        optionsBuilder.tlsHandshakeTimeout(timeout);
    }

    public Options options() {
        return optionsBuilder.build();
    }
}
