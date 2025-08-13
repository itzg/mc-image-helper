package me.itzg.helpers.files;

import io.netty.buffer.ByteBuf;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufFlux;
import reactor.util.function.Tuple2;

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
        final ByteBufQueue byteBufQueue = new ByteBufQueue();

        // Separate this into a pair of concurrent mono's
        return Mono.zip(
                // ...file writer
                Mono.fromCallable(() -> {
                        try (FileChannel channel = FileChannel.open(file,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                        )) {
                            ByteBuf byteBuf;
                            while ((byteBuf = byteBufQueue.take()) != null) {
                                try {
                                    //noinspection ResultOfMethodCallIgnored
                                    channel.write(byteBuf.nioBuffer());
                                } finally {
                                    byteBuf.release();
                                }
                            }

                            return file;
                        }
                    })
                    // ...which runs in a separate thread
                    .subscribeOn(Schedulers.boundedElastic()),
                // ...and the network consumer flux
                byteBufFlux
                    // Mark the bytebufs as retained so they can be released after
                    // they are written by the mono above
                    .retain()
                    .map(byteBuf -> {
                        final int amount = byteBuf.readableBytes();
                        byteBufQueue.add(byteBuf);
                        return amount;
                    })
                    .doOnTerminate(byteBufQueue::finish)
                    .collect(Collectors.<Integer>summingLong(value -> value))
            )
            // Just expose the total bytes read from network
            .map(Tuple2::getT2);
    }
}
