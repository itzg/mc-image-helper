package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
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

    public static Mono<Path> createDirectories(Path dir) {
        return Mono.fromCallable(() -> Files.createDirectories(dir))
            .subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Long> writeByteBufFluxToFile(ByteBufFlux byteBufFlux, Path file) {
        return Mono.fromCallable(() -> {
                    log.trace("Opening {} for writing", file);
                    return FileChannel.open(file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    );
                }
            )
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(outChannel ->
                byteBufFlux
                    .asByteBuffer()
                    .subscribeOn(Schedulers.boundedElastic())
                    .<Integer>handle((byteBuffer, sink) -> {
                        try {
                            sink.next(outChannel.write(byteBuffer));
                        } catch (IOException e) {
                            sink.error(Exceptions.propagate(e));
                        }
                    })
                    .doOnTerminate(() -> {
                        try {
                            outChannel.close();
                            log.trace("Closed {}", file);
                        } catch (IOException e) {
                            log.warn("Failed to close {}", file, e);
                        }
                    })
                    .collect(Collectors.<Integer>summingLong(value -> value))
            );
    }
}
