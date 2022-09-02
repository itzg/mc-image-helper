package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.get.ExtendedRequestRetryStrategy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

@Slf4j
public class FetchBuilder<SELF extends FetchBuilder<SELF>> {

    static class Config {
        private final URI uri;
        private List<String> acceptContentTypes;
        private String userAgent;
        private final Map<String, String> headers = new HashMap<>();

        private int retryCount = 5;
        private Duration retryDelay = Duration.ofSeconds(2);

        Config(URI uri) {
            this.uri = uri;
        }
    }
    private final Config config;

    public FetchBuilder(URI uri) {
        this.config = new Config(uri);
    }

    protected FetchBuilder(Config config) {
        this.config = config;
    }

    public SpecificFileFetchBuilder toFile(Path file) {
        return new SpecificFileFetchBuilder(this.config, file);
    }

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type) {
        return new ObjectFetchBuilder<>(this.config, type);
    }

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type, ObjectMapper objectMapper) {
        return new ObjectFetchBuilder<>(this.config, type, objectMapper);
    }

    protected HttpGet get() throws IOException {
        final HttpGet request = new HttpGet(config.uri);
        configureRequest(request);
        return request;
    }

    protected CloseableHttpClient client() {
        return configureClient(HttpClients.custom())
            .build();
    }

    public List<String> getAcceptContentTypes() {
        return config.acceptContentTypes;
    }

    public SELF acceptContentTypes(List<String> types) {
        config.acceptContentTypes = types;
        return self();
    }

    @SuppressWarnings("unused")
    public SELF userAgent(String userAgent) {
        config.userAgent = userAgent;
        return self();
    }

    @SuppressWarnings("unused")
    public SELF header(String name, String value) {
        config.headers.put(name, value);
        return self();
    }

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    @SuppressWarnings("unused")
    public SELF retry(int count, Duration delay) {
        config.retryCount = count;
        config.retryDelay = delay;
        return self();
    }

    protected HttpClientBuilder configureClient(HttpClientBuilder clientBuilder) {
        clientBuilder
            .addRequestInterceptorFirst((request, entity, context) -> {
                try {
                    log.debug("Request: {} {} with headers {}",
                        request.getMethod(), request.getUri(), Arrays.toString(request.getHeaders()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            })
            .useSystemProperties()
            .setUserAgent(config.userAgent)
            .setRetryStrategy(
                new ExtendedRequestRetryStrategy(config.retryCount, (int) config.retryDelay.getSeconds())
            );
        return clientBuilder;
    }

    protected void configureRequest(BasicHttpRequest request) throws IOException {
        if (config.acceptContentTypes != null) {
            for (final String type : config.acceptContentTypes) {
                request.addHeader(HttpHeaders.ACCEPT, type);
            }
        }

        for (final Entry<String, String> entry : config.headers.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
    }
}
