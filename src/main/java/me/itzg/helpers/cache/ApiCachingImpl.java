package me.itzg.helpers.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.cache.CacheIndex.CacheEntry;
import me.itzg.helpers.json.ObjectMappers;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ApiCachingImpl implements ApiCaching {

    private static final String CACHE_SUBIDR = ".cache";
    private static final String CACHE_INDEX_FILENAME = "cache-index.json";
    private final ObjectMapper objectMapper;
    private final CacheIndex cacheIndex;
    private final Path cacheNamespaceDir;

    @Setter
    private Duration defaultCacheDuration = Duration.ofHours(48);

    @Setter
    private Map<String/*operation*/,Duration> cacheDurations = new HashMap<>();

    @Blocking
    public ApiCachingImpl(Path outputDirectory, String namespace, @Nullable CacheArgs cacheArgs) throws IOException {
        if (cacheArgs != null) {
            defaultCacheDuration = cacheArgs.getDefaultCacheDuration();
            cacheDurations = cacheArgs.getCacheDurations();
        }
        objectMapper = ObjectMappers.defaultMapper();
        cacheNamespaceDir = outputDirectory.resolve(CACHE_SUBIDR).resolve(namespace);
        cacheIndex = loadCacheIndex();
        pruneExpiredEntries();
    }

    private void pruneExpiredEntries() {
        final Instant now = Instant.now();

        cacheIndex.getOperations().forEach((operation, entryMap) -> {
            final Iterator<Map.Entry<String, CacheEntry>> entries = entryMap.entrySet().iterator();
            while (entries.hasNext()) {
                final CacheEntry entry = entries.next().getValue();
                if (entry.getExpiresAt().isBefore(now)) {
                    final Path contentFile = resolveContentFile(operation, entry.getFilename());
                    try {
                        log.debug("Pruning cached content file {}", contentFile);
                        Files.delete(contentFile);
                    } catch (IOException e) {
                        log.warn("Failed to delete cached content file {}", contentFile, e);
                    }
                    entries.remove();
                }
            }
        });
    }

    private Path resolveContentFile(String operation, String filename) {
        return cacheNamespaceDir.resolve(operation).resolve(filename);
    }

    private CacheIndex loadCacheIndex() {
        final Path cacheIndexPath = cacheNamespaceDir.resolve(CACHE_INDEX_FILENAME);
        if (Files.exists(cacheIndexPath)) {
            log.debug("Loading cache index from {}", cacheIndexPath);
            try {
                return objectMapper.readValue(cacheIndexPath.toFile(), CacheIndex.class);
            } catch (IOException e) {
                log.warn("Failed to load API cache index from {}", cacheIndexPath, e);
                wipeCacheDirectory();
                return new CacheIndex();
            }
        }
        else {
            return new CacheIndex();
        }
    }

    private void wipeCacheDirectory() {
        if (!Files.exists(cacheNamespaceDir)) {
            log.debug("Skipping wipe of non-existent cache directory {}", cacheNamespaceDir);
            return;
        }

        log.debug("Wiping cache directory {}", cacheNamespaceDir);
        try {
            Files.walkFileTree(cacheNamespaceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".json")) {
                        try {
                            log.debug("Wiping cache file {}", file);
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("Failed to delete cache file {}", file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Unexpected failure while wiping cache directory", e);
        }
    }

    @Override
    public <R> Mono<R> cache(String operation, Class<R> returnType, Mono<R> resolver, Object... keys) {
        final String keysKey = Stream.of(keys)
            .map(Object::toString)
            .collect(Collectors.joining(","));

        return Mono.fromCallable(() -> {
            synchronized (cacheIndex) {
                final Map<String, CacheEntry> entryMap = cacheIndex.getOperations().get(operation);
                if (entryMap != null) {
                    return entryMap.get(keysKey);
                }
                return null;
            }
        })
            .flatMap(entry -> loadFromCache(operation, keysKey, entry, returnType))
            .switchIfEmpty(
                resolver
                    .flatMap(r -> saveToCache(operation, keysKey, r))
            );
    }

    private <R> Mono<R> saveToCache(String operation, String keys, R value) {

        return Mono.fromCallable(() -> {
                final String filename = UUID.randomUUID() + ".json";
                try {
                    final Path contentFile = Files.createDirectories(cacheNamespaceDir.resolve(operation))
                        .resolve(filename);

                    objectMapper.writeValue(contentFile.toFile(), value);
                    synchronized (cacheIndex) {
                        cacheIndex.getOperations().computeIfAbsent(operation, s -> new HashMap<>())
                            .put(keys, new CacheEntry()
                                .setExpiresAt(Instant.now().plus(
                                    lookupCacheDuration(operation)
                                ))
                                .setFilename(filename)
                            );
                    }

                    log.debug("Saved cache content of {}({}) to {}", operation, keys, contentFile);
                } catch (IOException e) {
                    log.warn("Failed to cache file for operation={} keys={}", operation, keys, e);
                }

                return value;

            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private TemporalAmount lookupCacheDuration(String operation) {
        return cacheDurations != null ?
            cacheDurations.getOrDefault(operation, defaultCacheDuration)
            : defaultCacheDuration;
    }

    private <R> Mono<R> loadFromCache(String operation, String keys, CacheEntry entry, Class<R> returnType) {

        return Mono.fromCallable(() -> {
                final Path contentFile = resolveContentFile(operation, entry.getFilename());
                if (Files.exists(contentFile)) {
                    log.debug("Loading cached content of {}({}) from {}", operation, keys, contentFile);
                    return objectMapper.readValue(contentFile.toFile(), returnType);
                }
                else {
                    return null;
                }
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Blocking
    public void close() throws IOException {
        Files.createDirectories(cacheNamespaceDir);
        final Path cacheIndexFile = cacheNamespaceDir.resolve(CACHE_INDEX_FILENAME);
        log.debug("Saving cache index to {}", cacheIndexFile);
        objectMapper.writeValue(cacheIndexFile.toFile(), cacheIndex);
    }
}
