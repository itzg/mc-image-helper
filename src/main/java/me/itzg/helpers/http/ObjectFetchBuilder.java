package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
public class ObjectFetchBuilder<T> extends FetchBuilderBase<ObjectFetchBuilder<T>> {

    private final Class<T> type;
    private final boolean listOf;
    private final ObjectReader reader;

    protected ObjectFetchBuilder(State state, Class<T> type, boolean listOf, ObjectMapper objectMapper) {
        super(state);
        this.type = type;
        this.listOf = listOf;
        if (listOf) {
            reader = objectMapper.readerForListOf(type);
        }
        else {
            reader = objectMapper.readerFor(type);
        }
    }

    public T execute() {
        return assemble().block();
    }

    public Mono<T> assemble() {
        return assembleCommon();
    }

    protected Mono<List<T>> assembleToList() {
        return assembleCommon();
    }

    private <R> Mono<R> assembleCommon() {
        return useReactiveClient(client ->
            client
                .headers(this::applyHeaders)
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "json fetch"))
                .get()
                .uri(uri())
                .responseSingle(this::handleResponse)
        );
    }

    private <R> Mono<R> handleResponse(HttpClientResponse resp, ByteBufMono bodyMono) {
        if (notSuccess(resp)) {
            return failedRequestMono(resp, "Fetching object content");
        }
        if (notExpectedContentType(resp)) {
            return failedContentTypeMono(resp);
        }

        return bodyMono.asInputStream()
            .publishOn(Schedulers.boundedElastic())
            .flatMap(inputStream -> {
                try {
                    try {
                        return Mono.just(reader.readValue(inputStream));
                    } catch (IOException e) {
                        return Mono.error(new GenericException(
                            "Failed to parse response body into " +
                                (listOf ? "list of " + type : type),
                            e
                        ));
                    }
                } finally {
                    try {
                        //noinspection BlockingMethodInNonBlockingContext
                        inputStream.close();
                    } catch (IOException e) {
                        log.warn("Unable to close body input stream", e);
                    }
                }
            });
    }
}
