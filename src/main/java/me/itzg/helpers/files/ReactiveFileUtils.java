package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufFlux;

@Slf4j
public class ReactiveFileUtils {

    /**
     * @return if the file exists, the last modified time or empty if file does not exist.
     * Returned Mono is already subscribed on bounded elastic scheduler and is cached.
     */
    public static Mono<Instant> getLastModifiedTime(Path file) {
        return Mono.fromCallable(() -> Files.exists(file) ?
                Files.getLastModifiedTime(file).toInstant() : null
            )
            .cache()
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * @return Returned Mono is already subscribed on bounded elastic scheduler.
     */
    public static Mono<Boolean> fileExists(Path file) {
        return Mono.fromCallable(() -> Files.exists(file))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public static Mono<Long> copyByteBufFluxToFile(ByteBufFlux byteBufFlux, Path file) {
        return Mono.fromCallable(() -> {
                    log.trace("Opening {} for writing", file);
                    return Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    );
                }
            )
            .flatMap(outChannel -> byteBufFlux.asByteBuffer()
                .flatMap(byteBuffer ->
                    Mono.fromCallable(() -> {
                                final int count = outChannel.write(byteBuffer);
                                log.trace("Wrote {} bytes to {}", count, file);
                                return count;
                            }
                        )
                )
                .doOnTerminate(() -> {
                    try {
                        log.trace("Closing file for writing: {}", file);
                        outChannel.close();
                    } catch (IOException e) {
                        log.error("Failed to close file for writing: {}", file, e);
                    }
                })
                .collect(Collectors.<Integer>summingLong(value -> value))
                .subscribeOn(Schedulers.boundedElastic())
            );
    }
}
