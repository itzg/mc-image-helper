package me.itzg.helpers.files;

import java.io.IOException;
import reactor.core.publisher.Mono;

public class DisabledApiCaching implements ApiCaching {

    @Override
    public <R> Mono<R> cache(String operation, Class<R> returnType, Mono<R> resolver, Object... keys) {
        return resolver;
    }

    @Override
    public void close() throws IOException {

    }
}
