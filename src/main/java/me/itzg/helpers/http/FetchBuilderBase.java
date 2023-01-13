package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

@Slf4j
public class FetchBuilderBase<SELF extends FetchBuilderBase<SELF>> {

    static class State {
        private final SharedFetch sharedFetch;
        private final URI uri;
        public String userAgentCommand;
        private List<String> acceptContentTypes;
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

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type) {
        return new ObjectFetchBuilder<>(this.state, type);
    }

    public <T> ObjectListFetchBuilder<T> toObjectList(Class<T> type) {
        return new ObjectListFetchBuilder<>(this.state, type);
    }

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type, ObjectMapper objectMapper) {
        return new ObjectFetchBuilder<>(this.state, type, objectMapper);
    }

    protected HttpGet get() throws IOException {
        final HttpGet request = new HttpGet(state.uri);
        configureRequest(request);
        return request;
    }

    protected HttpHead head(boolean withConfigure) throws IOException {
        final HttpHead request = new HttpHead(state.uri);
        if (withConfigure) {
            configureRequest(request);
        }
        return request;
    }

    protected URI uri() {
        return state.uri;
    }

    public List<String> getAcceptContentTypes() {
        return state.acceptContentTypes;
    }

    public SELF acceptContentTypes(List<String> types) {
        state.acceptContentTypes = types;
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

    protected interface ClientUser<R> {
        R use(HttpClient client) throws IOException;
    }

    /**
     * Intended to be called by subclass specific <code>execute</code> methods.
     * @param user provided either a multi-request {@link SharedFetch} or an instance scoped to this call.
     *                Either way, {@link SharedFetch#getClient()} can be used to execute requests.
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

    /**
     * Intended to be called by subclass specific <code>execute</code> methods.
     * This is a convenience version of {@link #usePreparedFetch(PreparedFetchUser)}
     * that provides just the {@link HttpClient} to execute requests.
     */
    protected <R> R useClient(ClientUser<R> user) throws IOException {
        return usePreparedFetch(sharedFetch ->
            user.use(sharedFetch.getClient())
        );
    }

    protected void configureRequest(BasicHttpRequest request) throws IOException {
        if (state.acceptContentTypes != null) {
            for (final String type : state.acceptContentTypes) {
                request.addHeader(HttpHeaders.ACCEPT, type);
            }
        }

        for (final Entry<String, String> entry : state.requestHeaders.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
        // and apply shared headers that weren't overridden by per-request
        if (state.sharedFetch != null) {
            for (final Entry<String, String> entry : state.sharedFetch.getHeaders().entrySet()) {
                if (!state.requestHeaders.containsKey(entry.getKey())) {
                    request.addHeader(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
