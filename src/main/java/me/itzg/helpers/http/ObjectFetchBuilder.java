package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ObjectFetchBuilder<T> extends FetchBuilderBase<ObjectFetchBuilder<T>> {

    private final Class<T> type;
    private final ObjectMapper objectMapper;

    ObjectFetchBuilder(State state, Class<T> type, ObjectMapper objectMapper) {
        super(state);
        this.type = type;
        this.objectMapper = objectMapper;
    }

    ObjectFetchBuilder(State state, Class<T> type) {
        this(state, type, ObjectMappers.defaultMapper());
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
    }

    public T execute() throws IOException {
        return assemble().block();
    }

    public Mono<T> assemble() throws IOException {
        return usePreparedFetch(sharedFetch ->
            sharedFetch.getReactiveClient()
                .headers(headers ->
                        headers.set(HttpHeaderNames.ACCEPT, "application/json")
                    )
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "json fetch"))
                .get()
                .uri(uri())
                .responseContent()
                .aggregate()
                .asInputStream()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(inputStream -> {
                    try {
                        try {
                            return Mono.just(objectMapper.readValue(inputStream, type));
                        } catch (IOException e) {
                            return Mono.error(new GenericException("Failed to parse response body into " + type, e));
                        }
                    }
                    finally {
                        try {
                            //noinspection BlockingMethodInNonBlockingContext
                            inputStream.close();
                        } catch (IOException e) {
                            log.warn("Unable to close body input stream", e);
                        }
                    }
                })
        );
    }

}
