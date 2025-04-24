package me.itzg.helpers.http;

import java.util.concurrent.Semaphore;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ConcurrencyLimiterImpl implements ConcurrencyLimiter {

    private final Semaphore semaphore;

    public ConcurrencyLimiterImpl(int concurrency) {
        this.semaphore = new Semaphore(concurrency);
    }

    @Override
    public <T> Mono<T> limit(Mono<T> source) {
        return Mono.using(
            () -> {
                semaphore.acquire();
                return Boolean.TRUE;
            },
            r -> source,
            r -> semaphore.release()
        )
            .subscribeOn(Schedulers.boundedElastic());
    }
}
