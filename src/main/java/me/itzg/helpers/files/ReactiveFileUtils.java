package me.itzg.helpers.files;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    /**
     * @return Returned Mono is already subscribed on bounded elastic scheduler.
     */
    public static Mono<Long> copyInputStreamToFile(InputStream inputStream, Path outputFile) {
        return Mono.fromCallable(() -> Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING))
            .subscribeOn(Schedulers.boundedElastic());
    }

}
