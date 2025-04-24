package me.itzg.helpers.http;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ConcurrencyLimiter {

    private final Semaphore semaphore;

    public ConcurrencyLimiter(int concurrency) {
        this.semaphore = new Semaphore(concurrency);
    }

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

    public Mono<?> limit() {
        return Mono.fromFuture(CompletableFuture.runAsync(() -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public void release() {
        semaphore.release();
    }
}
