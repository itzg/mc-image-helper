package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

public class ObjectListFetchBuilder<T> extends FetchBuilderBase<ObjectListFetchBuilder<T>> {

    private final ObjectFetchBuilder<T> delegate;

    ObjectListFetchBuilder(State state, Class<T> type, ObjectMapper objectMapper) {
        super(state);

        delegate = new ObjectFetchBuilder<>(state, type, true, objectMapper);
    }

    public List<T> execute() throws IOException {
        return delegate.assembleToList()
            .block();
    }

    public Mono<List<T>> assemble() {
        return delegate.assembleToList();
    }
}
