package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.json.ObjectMappers;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientForm;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
public class FetchBuilderBase<SELF extends FetchBuilderBase<SELF>> {

    final static DateTimeFormatter httpDateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
    private static final Pattern HEADER_KEYS_TO_REDACT = Pattern.compile("authorization|api-key", Pattern.CASE_INSENSITIVE);

    static protected class State {
        private final SharedFetch sharedFetch;
        private final URI uri;
        private final String userInfo;
        public String userAgentCommand;
        private Set<String> acceptContentTypes;
        private final Map<String, String> requestHeaders = new HashMap<>();

        State(URI uri, SharedFetch sharedFetch) {
            // Netty seems to half-way URL encode paths that have unicode,
            // so instead we'll pre-"encode" the URI
            final URI encoded = URI.create(uri.toASCIIString());

            if (uri.getRawUserInfo() != null) {
                this.userInfo = uri.getRawUserInfo();
                try {
                    this.uri = new URI(
                        encoded.getScheme(),
                        // just show first letter of username for sanity confirmation
                        encoded.getRawUserInfo().charAt(0) + "***:***",
                        encoded.getHost(),
                        encoded.getPort(),
                        encoded.getPath(),
                        encoded.getQuery(),
                        encoded.getFragment()
                    );
                } catch (URISyntaxException e) {
                    throw new GenericException("Failed to redact user info", e);
                }
            }
            else {
                this.userInfo = null;
                this.uri = encoded;
            }
            this.sharedFetch = sharedFetch;
        }
    }
    private final State state;

    FetchBuilderBase(URI uri) {
        this.state = new State(uri, null);
    }

    FetchBuilderBase(URI uri, SharedFetch sharedFetch) {
        this.state = new State(uri, sharedFetch);
    }

    protected FetchBuilderBase(State state) {
        this.state = state;
    }

    public SpecificFileFetchBuilder toFile(Path file) {
        return new SpecificFileFetchBuilder(this.state, file);
    }

    @SuppressWarnings("unused")
    public OutputToDirectoryFetchBuilder toDirectory(Path directory) {
        return new OutputToDirectoryFetchBuilder(this.state, directory);
    }

    public StringFetchBuilder asString() {
        return new StringFetchBuilder(this.state);
    }

    /**
     * NOTE: this will set expected content types to application/json
     */
    public <T> ObjectFetchBuilder<T> toObject(Class<T> type) {
        acceptContentTypes(Collections.singletonList("application/json"));
        return new ObjectFetchBuilder<>(this.state, type, false, ObjectMappers.defaultMapper());
    }

    protected  <T> ObjectFetchBuilder<T> toObject(Class<T> type, RequestAssembler requestAssembler) {
        acceptContentTypes(Collections.singletonList("application/json"));
        return new ObjectFetchBuilder<>(this.state, type, false, ObjectMappers.defaultMapper(), requestAssembler);
    }

    /**
     * NOTE: this will set expected content types to application/json
     */
    public <T> ObjectListFetchBuilder<T> toObjectList(Class<T> type) {
        acceptContentTypes(Collections.singletonList("application/json"));
        return new ObjectListFetchBuilder<>(this.state, type, ObjectMappers.defaultMapper());
    }

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type, ObjectMapper objectMapper) {
        return new ObjectFetchBuilder<>(this.state, type, false, objectMapper);
    }

    public FormFetchBuilder sendForm(Consumer<HttpClientForm> prepareForm) {
        return new FormFetchBuilder(state, prepareForm);
    }

    protected URI uri() {
        return state.uri;
    }

    public Set<String> getAcceptContentTypes() {
        return state.acceptContentTypes;
    }

    public SELF acceptContentTypes(List<String> types) {
        state.acceptContentTypes = types != null ? new HashSet<>(types) : Collections.emptySet();
        return self();
    }

    public SELF userAgentCommand(String userAgentCommand) {
        state.userAgentCommand = userAgentCommand;
        return self();
    }

    @SuppressWarnings("unused")
    public SELF header(String name, String value) {
        state.requestHeaders.put(name, value);
        return self();
    }

    /**
     * Helps with fluent sub-type builder pattern
     */
    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    @FunctionalInterface
    protected interface ReactiveClientUser<R> {

        R use(HttpClient client);
    }

    protected <R> R useReactiveClient(ReactiveClientUser<R> user) {
        if (state.sharedFetch != null) {
            return user.use(state.sharedFetch.getReactiveClient());
        }
        else {
            try (SharedFetch sharedFetch = new SharedFetch(state.userAgentCommand, Options.builder().build())) {
                return user.use(sharedFetch.getReactiveClient());
            }
        }
    }

    protected static BiConsumer<? super HttpClientRequest, ? super Connection> debugLogRequest(
        Logger log, String operation
    ) {
        return (req, connection) -> {
            if (log.isDebugEnabled()) {
                log.debug("{}: uri={} headers={}",
                        operation.toUpperCase(), req.resourceUrl(), applyHeaderRedactions(req.requestHeaders()
                ));
            }
        };
    }

    private static List<String> applyHeaderRedactions(HttpHeaders headers) {
        return headers.entries().stream()
                .map(entry -> {
                    final String key = entry.getKey();
                    final Matcher m = HEADER_KEYS_TO_REDACT.matcher(key);
                    if (m.find()) {
                        return key + ": [redacted]";
                    }
                    else {
                        return key + ": " + entry.getValue();
                    }
                })
                .collect(Collectors.toList());
    }

    protected  <R> Mono<R> failedRequestMono(HttpClientResponse resp, ByteBufMono bodyMono, String description) {
        return (bodyMono != null ? bodyMono.asString() : Mono.just(""))
            .defaultIfEmpty("")
            .flatMap(body -> Mono.error(new FailedRequestException(resp.status(), uri(), body, description, resp.responseHeaders())));
    }

    protected static boolean notSuccess(HttpClientResponse resp) {
        return HttpStatusClass.valueOf(resp.status().code()) != HttpStatusClass.SUCCESS;
    }

    /**
     * @return false if response content type is not one of the expected content types,
     * but true if no expected content types
     */
    protected boolean notExpectedContentType(HttpClientResponse resp) {
        final Set<String> contentTypes = getAcceptContentTypes();
        if (contentTypes != null && !contentTypes.isEmpty()) {
            final List<String> respTypes = resp.responseHeaders()
                .getAll(CONTENT_TYPE);

            return respTypes.stream().noneMatch(s -> contentTypes.contains((String)HttpUtil.getMimeType(s)));
        }
        return false;
    }

    /**
     * CDN providers like bukkit don't support HEAD and respond with a 200 OK and redirect to a path like /error?aspxerrorpath=/projects/worldedit/files/latest
     * @param resp the response to evaluate
     * @return true if this response to a HEAD seems to indicate it is not supported
     */
    protected static boolean isHeadUnsupportedResponse(HttpClientResponse resp) {
        return resp.path().equals("error") &&
            HttpUtil.getMimeType(resp.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE.toString())).equals("text/html");
    }

    protected <R> Mono<R> failedContentTypeMono(HttpClientResponse resp) {
        return Mono.error(new GenericException(
            String.format("Unexpected content type in response. Expected '%s' but got '%s'",
                getAcceptContentTypes(), resp.responseHeaders()
                    .getAll(CONTENT_TYPE)
            )));
    }

    protected void applyHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        final Set<String> contentTypes = getAcceptContentTypes();
        if (contentTypes != null && !contentTypes.isEmpty()) {
            headers.set(
                ACCEPT.toString(),
                contentTypes
            );
        }

        if (state.userInfo != null) {
            headers.set(
                AUTHORIZATION.toString(),
                "Basic " +
                    Base64.getEncoder().encodeToString(
                        state.userInfo.getBytes(StandardCharsets.UTF_8)
                    )
            );
        }

        state.requestHeaders.forEach(headers::set);
    }

    static String formatDuration(long millis) {
        final StringBuilder sb = new StringBuilder();
        final long minutes = millis / 60000;
        if (minutes > 0) {
            sb.append(minutes);
            sb.append("m ");
        }
        final long seconds = (millis % 60000) / 1000;
        if (seconds > 0) {
            sb.append(seconds);
            sb.append("s ");
        }
        sb.append(millis % 1000);
        sb.append("ms");
        return sb.toString();
    }

    static String transferRate(long millis, long bytes) {
        return String.format("%d KB/s", bytes / millis);
    }
}
