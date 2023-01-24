package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.client5.http.HttpResponseException;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
public class FetchBuilderBase<SELF extends FetchBuilderBase<SELF>> {

    static class State {
        private final SharedFetch sharedFetch;
        private final URI uri;
        public String userAgentCommand;
        private Set<String> acceptContentTypes;
        private final Map<String, String> requestHeaders = new HashMap<>();

        State(URI uri, SharedFetch sharedFetch) {
            this.uri = uri;
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

    /**
     * NOTE: this will set expected content types to application/json
     */
    public <T> ObjectFetchBuilder<T> toObject(Class<T> type) {
        acceptContentTypes(Collections.singletonList("application/json"));
        return new ObjectFetchBuilder<>(this.state, type, false, ObjectMappers.defaultMapper());
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

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    protected interface PreparedFetchUser<R> {
        R use(SharedFetch sharedFetch) throws IOException;
    }

    /**
     * Intended to be called by subclass specific <code>execute</code> methods.
     */
    protected <R> R usePreparedFetch(PreparedFetchUser<R> user) throws IOException {
        if (state.sharedFetch != null) {
            try {
                return user.use(state.sharedFetch);
            } catch (HttpResponseException e) {
                throw new FailedRequestException(e, state.uri);
            } finally {
                state.sharedFetch.getLatchingUrisInterceptor().reset();
            }
        }
        else {
            try (SharedFetch sharedFetch = new SharedFetch(state.userAgentCommand)) {
                try {
                    return user.use(sharedFetch);
                } catch (HttpResponseException e) {
                    throw new FailedRequestException(e, state.uri);
                }
            }
        }
    }

    protected static BiConsumer<? super HttpClientRequest, ? super Connection> debugLogRequest(
        Logger log, String operation
    ) {
        return (req, connection) ->
            log.debug("{}: uri={} headers={}",
                operation.toUpperCase(), req.resourceUrl(), req.requestHeaders()
            );
    }

    protected  <R> Mono<R> failedRequestMono(HttpClientResponse resp, String description) {
        return Mono.error(new FailedRequestException(resp.status(), uri(), description));
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

        state.requestHeaders.forEach(headers::set);
    }
}
