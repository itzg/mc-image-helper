package me.itzg.helpers.files;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Function;
import java.util.jar.JarFile;
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

    /**
     * Used with {@link reactor.core.publisher.Mono#onErrorResume(java.util.function.Function)}
     * @param throwable the throwable to pass to the returned mono
     * @param outputFile the file to remove, if present
     * @return an error mono with the given throwable as cause
     * @param <T> the type of the surrounding reactive chain
     */
    public static <T> Mono<T> removeFailedDownload(Throwable throwable, Path outputFile) {
        return Mono.<T>create(sink -> {
                try {
                    log.trace("Removing failed download of {}", outputFile);
                    if (Files.deleteIfExists(outputFile)) {
                        log.debug("Removed failed download of {}", outputFile);
                    }
                } catch (IOException e) {
                    log.error("Unable to remove failed download of {}", outputFile, e);
                }
                sink.error(throwable);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    public static Function<Path, Mono<Path>> moveTo(Path to) {
        return from -> Mono.fromCallable(() -> {
                log.debug("Moving {} to {}", from, to);
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                return to;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * A regular file with {@link #hasZipMagic(Path) ZIP magic} that opens as a {@link JarFile}.
     * Magic alone accepts a truncated download; opening the jar reads the central directory.
     *
     * @return {@code true} when {@code path} is a readable jar
     */
    public static boolean isValidJar(Path path) {
        try {
            if (!Files.isRegularFile(path) || !hasZipMagic(path)) {
                return false;
            }
            try (JarFile jarFile = new JarFile(path.toFile(), false)) {
                log.debug("Opened jar {} with {} entries", path, jarFile.size());
                return true;
            }
        } catch (IOException e) {
            log.debug("Unable to open {} as a jar", path, e);
            return false;
        }
    }

    /**
     * ZIP local-file, empty-archive, or spanned-archive magic.
     *
     * @return {@code false} when the file is shorter than four bytes or the header is not ZIP magic
     * @throws IOException when the file cannot be read
     */
    public static boolean hasZipMagic(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            final byte[] header = new byte[4];
            final int read = in.read(header);
            if (read < 4) {
                return false;
            }
            return header[0] == 0x50 && header[1] == 0x4b
                && (
                    (header[2] == 0x03 && header[3] == 0x04)
                    || (header[2] == 0x05 && header[3] == 0x06)
                    || (header[2] == 0x07 && header[3] == 0x08)
                );
        }
    }

    /**
     * @return {@code path} when {@link #isValidJar(Path)} accepts it, otherwise empty.
     * Returned Mono is already subscribed on the bounded elastic scheduler.
     */
    public static Mono<Path> validJar(Path path) {
        return Mono.fromCallable(() -> isValidJar(path))
            .filter(Boolean::booleanValue)
            .map(valid -> path)
            .subscribeOn(Schedulers.boundedElastic());
    }
}
