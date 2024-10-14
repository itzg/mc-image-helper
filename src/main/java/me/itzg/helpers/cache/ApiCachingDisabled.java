package me.itzg.helpers.cache;

import java.io.IOException;
import reactor.core.publisher.Mono;

public class ApiCachingDisabled implements ApiCaching {

    @Override
    public <R> Mono<R> cache(String operation, Class<R> returnType, Mono<R> resolver, Object... keys) {
        return resolver;
    }

    @Override
    public void close() throws IOException {

    }
}
