package me.itzg.helpers.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpStatusClass;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.json.ObjectMappers;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
public class ReactorNettyBits {

    private final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    @FunctionalInterface
    interface ValueReader<V> {

        V readFrom(InputStream inputStream) throws IOException;
    }

    public <V> BiFunction<HttpClientResponse, ByteBufMono, Mono<V>> readInto(
        Class<V> valueType
    ) {
        return receiver(inputStream -> objectMapper.readValue(inputStream, valueType));
    }

    public <V> BiFunction<HttpClientResponse, ByteBufMono, Mono<V>> readInto(
        TypeReference<V> typeReference
    ) {
        return receiver(inputStream -> objectMapper.readValue(inputStream, typeReference));
    }

    private <V> BiFunction<HttpClientResponse, ByteBufMono, Mono<V>> receiver(
        ValueReader<V> valueReader
    ) {
        return (httpClientResponse, byteBufMono) -> {
            HttpStatusClass statusClass = HttpStatusClass.valueOf(httpClientResponse.status().code());
            if (statusClass == HttpStatusClass.SUCCESS) {
                return byteBufMono.asInputStream()
                    .map(inputStream -> {
                        try {
                            return valueReader.readFrom(inputStream);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to parse version response", e);
                        }
                    });
            } else {
                return byteBufMono.asString()
                    .flatMap(
                        body -> Mono.error(new HttpClientException(httpClientResponse.status(), body)));
            }

        };
    }

    public HttpClient jsonClient() {
        return client()
            .headers(entries -> entries.add(HttpHeaderNames.ACCEPT, "application/json"));
    }

    public HttpClient client() {
        return HttpClient.create()
            .headers(entries -> entries.add(HttpHeaderNames.USER_AGENT, "mc-image-helper"))
            .doOnRequest((req, connection) ->
                log.debug("{} {} headers={}",
                    req.method(), req.uri(), req.requestHeaders()
                )
            )
            .doOnResponse((resp, connection) ->
                log.debug("Response status={}", resp.status())
            );
    }
}
