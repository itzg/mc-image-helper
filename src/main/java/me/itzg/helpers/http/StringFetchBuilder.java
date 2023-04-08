package me.itzg.helpers.http;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
public class StringFetchBuilder extends FetchBuilderBase<StringFetchBuilder> {
    public StringFetchBuilder(State state) {
        super(state);
    }

    public Mono<String> assemble() {
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

    private Mono<String> handleResponse(HttpClientResponse resp, ByteBufMono byteBufMono) {
        return byteBufMono.asString();
    }
}
