package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.io.IOException;
import java.util.List;

@Slf4j
public class ObjectFetchBuilder<T> extends FetchBuilderBase<ObjectFetchBuilder<T>>
    implements RequestResponseAssembler<T>
{

    private final Class<T> type;
    private final boolean listOf;
    private final ObjectReader reader;
    private final RequestAssembler requestAssembler;

    protected ObjectFetchBuilder(State state, Class<T> type, boolean listOf, ObjectMapper objectMapper) {
        this(state, type, listOf, objectMapper, null);
    }

    protected ObjectFetchBuilder(State state, Class<T> type, boolean listOf, ObjectMapper objectMapper, RequestAssembler requestAssembler) {
        super(state);
        this.type = type;
        this.listOf = listOf;
        if (listOf) {
            reader = objectMapper.readerForListOf(type);
        }
        else {
            reader = objectMapper.readerFor(type);
        }
        this.requestAssembler = requestAssembler != null ? requestAssembler : this::assembleRequest;
    }

    public T execute() {
        return assemble().block();
    }

    @Override
    public Mono<T> assemble() {
        return assembleCommon();
    }

    protected Mono<List<T>> assembleToList() {
        return assembleCommon();
    }

    private <R> Mono<R> assembleCommon() {
        return useReactiveClient(client ->
            requestAssembler.assembleRequest(client)
                .responseSingle(this::handleResponse)
        );
    }

    private HttpClient.ResponseReceiver<?> assembleRequest(HttpClient client) {
        return client
                .headers(this::applyHeaders)
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "json fetch"))
                .get()
                .uri(uri());
    }

    private <R> Mono<R> handleResponse(HttpClientResponse resp, ByteBufMono bodyMono) {
        if (notSuccess(resp)) {
            return failedRequestMono(resp, bodyMono, "Fetching object content");
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
