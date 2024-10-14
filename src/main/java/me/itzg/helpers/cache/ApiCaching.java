package me.itzg.helpers.cache;

import java.io.IOException;
import reactor.core.publisher.Mono;

public interface ApiCaching extends AutoCloseable {

    <R> Mono<R> cache(String operation, Class<R> returnType, Mono<R> resolver, Object... keys);

    void close() throws IOException;
}
