package me.itzg.helpers.mvn;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.function.Supplier;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class MavenRepoApi {

    public static final TemporalAmount MAX_CACHE_AGE = Duration.ofHours(1);

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;
    private final XmlMapper xmlMapper;
    @Setter
    private Path metadataCacheDir;
    @Setter
    private boolean skipExisting;
    @Setter
    private boolean skipUpToDate;
    @Setter
    private Supplier<Instant> instantSource = Instant::now;

    public MavenRepoApi(String repoUrl, SharedFetch sharedFetch) {
        uriBuilder = UriBuilder.withBaseUrl(repoUrl);
        this.sharedFetch = sharedFetch;
        xmlMapper = new XmlMapper();
    }

    public Mono<MavenMetadata> fetchMetadata(String groupId, String artifactId) {
        final Mono<MavenMetadata> cachedMono = loadFromCache(groupId, artifactId);

        return cachedMono
            .switchIfEmpty(retrieveMetadata(groupId, artifactId)
                .flatMap(mavenMetadata -> {
                    if (metadataCacheDir != null) {
                        return cacheMetadata(groupId, artifactId, mavenMetadata);
                    }
                    else {
                        return Mono.just(mavenMetadata);
                    }
                })
            );
    }

    @NotNull
    private Mono<MavenMetadata> retrieveMetadata(String groupId, String artifactId) {
        final String groupPath = groupId.replace('.', '/');

        return sharedFetch
            .fetch(
                uriBuilder.resolve("/{raw:groupPath}/{artifactId}/maven-metadata.xml",
                    groupPath, artifactId
                )
            )
            .toObject(MavenMetadata.class, xmlMapper)
            .assemble()
            .checkpoint(String.format("fetching metadata for %s:%s", groupId, artifactId));
    }

    @NotNull
    private Mono<MavenMetadata> cacheMetadata(String groupId, String artifactId, MavenMetadata mavenMetadata) {
        return Mono.fromSupplier(() -> {
                try {
                    final Path dir = Files.createDirectories(metadataCacheDir);
                    final Path metadataFile = resolveMetadataFile(groupId, artifactId, dir);
                    log.debug("Caching {}:{} metadata to {}", groupId, artifactId, metadataFile);
                    xmlMapper.writeValue(metadataFile.toFile(), mavenMetadata);
                } catch (URISyntaxException | IOException e) {
                    throw new GenericException("Caching maven metadata", e);
                }
                return mavenMetadata;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * @return cached file or empty is absent or too old
     */
    @NotNull
    private Mono<MavenMetadata> loadFromCache(String groupId, String artifactId) {
        final Mono<MavenMetadata> cachedMono;
        if (metadataCacheDir != null) {
            cachedMono = Mono.defer(() -> {
                try {
                    final Path metadataFile = resolveMetadataFile(groupId, artifactId, metadataCacheDir);
                    if (Files.exists(metadataFile)) {
                        @SuppressWarnings("BlockingMethodInNonBlockingContext") // has subscribeOn below
                        final FileTime modifiedTime = Files.getLastModifiedTime(metadataFile);
                        log.debug("Metadata cache file exists at={} modified={}", metadataFile, modifiedTime);
                        if (modifiedTime.toInstant().isAfter(instantSource.get().minus(MAX_CACHE_AGE))) {
                            return Mono.just(xmlMapper.readValue(metadataFile.toFile(), MavenMetadata.class));
                        }
                    }
                    return Mono.empty();
                } catch (URISyntaxException | IOException e) {
                    return Mono.error(new GenericException("Reading metadata cache", e));
                }
            })
                .subscribeOn(Schedulers.boundedElastic());
        }
        else {
            cachedMono = Mono.empty();
        }
        return cachedMono;
    }

    @NotNull
    private Path resolveMetadataFile(String groupId, String artifactId, Path dir) throws URISyntaxException {
        final URI uri = new URI(uriBuilder.getBaseUrl());
        final String host = uri.getHost();
        return dir.resolve(String.format("%s-%s-%s.xml", host, groupId, artifactId));
    }

    /**
     * @param packaging  such as "jar"
     * @param classifier may be null
     */
    public Mono<Path> download(Path outputDirectory, String groupId, String artifactId, String version,
        String packaging, String classifier
    ) {
        final String groupPath = groupId.replace('.', '/');

        Mono<String> resolvedVersionMono;
        if (version == null || version.equalsIgnoreCase("release")) {
            resolvedVersionMono = fetchMetadata(groupId, artifactId)
                .map(mavenMetadata -> mavenMetadata.getVersioning().getRelease());
        } else if (version.equalsIgnoreCase("latest")) {
            resolvedVersionMono = fetchMetadata(groupId, artifactId)
                .map(mavenMetadata -> mavenMetadata.getVersioning().getLatest());
        } else {
            resolvedVersionMono = Mono.just(version);
        }

        return resolvedVersionMono
            .flatMap(resolvedVersion -> {
                final String filename = String.format("%s-%s%s.%s",
                    artifactId, resolvedVersion, classifier != null ? "-" + classifier : "", packaging
                );

                return sharedFetch.fetch(
                        uriBuilder.resolve("/{raw:groupPath}/{artifact}/{version}/{filename}",
                            groupPath, artifactId, resolvedVersion, filename
                        )
                    )
                    .toFile(outputDirectory.resolve(filename))
                    .skipExisting(skipExisting)
                    .skipUpToDate(skipUpToDate)
                    .assemble()
                    .checkpoint(String.format("Downloading %s:%s:%s%s.%s",
                        groupId, artifactId, resolvedVersion, classifier != null ? ":"+classifier : "",
                        packaging
                    ));
            });
    }
}
