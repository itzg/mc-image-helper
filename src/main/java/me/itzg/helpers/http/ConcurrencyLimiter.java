package me.itzg.helpers.http;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ConcurrencyLimiter {

    ConcurrencyLimiter NOOP_LIMITER = new ConcurrencyLimiter() {
        @Override
        public <T> Mono<T> limit(Mono<T> source) {
            return source;
        }
    };

    <T> Mono<T> limit(Mono<T> source);
}
