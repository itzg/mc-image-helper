package me.itzg.helpers.fabric;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidContentException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
public class FabricMetaClient {

    private final SharedFetch sharedFetch;
    private final UriBuilder uriBuilder;

    /**
     * Retry attempts for metadata, non-downloads
     */
    @Setter
    private long retryMaxAttempts = 5;
    /**
     * Retry minimum backoff for metadata, non-downloads
     */
    @Setter
    private Duration retryMinBackoff = Duration.ofMillis(500);

    @Setter
    private int downloadRetryMaxAttempts = 10;
    @Setter
    private Duration downloadRetryMinBackoff = Duration.ofMillis(500);

    public FabricMetaClient(SharedFetch sharedFetch, String fabricMetaBaseUrl) {
        this.sharedFetch = sharedFetch;
        uriBuilder = UriBuilder.withBaseUrl(fabricMetaBaseUrl);
    }

    static boolean nonEmptyString(String loaderVersion) {
        return loaderVersion != null && !loaderVersion.isEmpty();
    }

    /**
     * @param version can be latest, snapshot or specific
     */
    public Mono<String> resolveMinecraftVersion(@Nullable String version) {
        if (!isLatest(version) && !isSnapshot(version)) {
            return Mono.just(version);
        }

        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/versions/game")
            )
            .toObjectList(VersionEntry.class)
            .assemble()
            .flatMap(versionEntries -> {
                if (isLatest(version)) {
                    return findFirst(versionEntries, VersionEntry::isStable)
                        .switchIfEmpty(Mono.error(() -> new GenericException("Unable to find any stable versions")));
                }
                else if (isSnapshot(version)) {
                    return findFirst(versionEntries, versionEntry -> !versionEntry.isStable())
                        .switchIfEmpty(Mono.error(() -> new GenericException("Unable to find any unstable versions")));
                }
                else {
                    return findFirst(versionEntries, versionEntry -> versionEntry.getVersion().equalsIgnoreCase(version))
                        .switchIfEmpty(Mono.error(() -> new GenericException("Unable to find requested version")));
                }
            })
            .retryWhen(Retry.backoff(retryMaxAttempts, retryMinBackoff).filter(IOException.class::isInstance))
            .checkpoint();
    }

    private static boolean isSnapshot(@Nullable String version) {
        return version != null && version.equalsIgnoreCase("snapshot");
    }

    private static boolean isLatest(@Nullable String version) {
        return version == null || version.equalsIgnoreCase("latest");
    }

    public Mono<String> resolveLoaderVersion(String minecraftVersion, String loaderVersion) {
        if (nonEmptyString(loaderVersion)) {
            return Mono.just(loaderVersion);
        }

        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/versions/loader/{game_version}", minecraftVersion))
            .toObjectList(LoaderResponseEntry.class)
            .assemble()
            .flatMap(loaderResponse -> {
                if (loaderResponse.isEmpty()) {
                    return Mono.error(new GenericException("No loader entries provided from " + uriBuilder.getBaseUrl()));
                }

                final VersionEntry loader = loaderResponse.stream()
                    .filter(entry -> entry.getLoader() != null && entry.getLoader().isStable())
                    .findFirst()
                    .orElseThrow(() -> new GenericException("No stable loaders found from " + uriBuilder.getBaseUrl()))
                    .getLoader();

                return Mono.just(loader.getVersion());
            })
            .retryWhen(Retry.backoff(retryMaxAttempts, retryMinBackoff).filter(IOException.class::isInstance))
            .checkpoint();
    }

    public Mono<String> resolveInstallerVersion(String installerVersion) {
        if (nonEmptyString(installerVersion)) {
            return Mono.just(installerVersion);
        }

        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/versions/installer")
            )
            .toObjectList(InstallerEntry.class)
            .assemble()
            .flatMap(installerEntries -> installerEntries.stream()
                .filter(InstallerEntry::isStable)
                .findFirst()
                .map(installerEntry -> Mono.just(installerEntry.getVersion()))
                .orElseGet(
                    () -> Mono.error(new GenericException("Failed to find stable installer from " + uriBuilder.getBaseUrl()))
                )
            )
            .retryWhen(Retry.backoff(retryMaxAttempts, retryMinBackoff).filter(IOException.class::isInstance))
            .checkpoint();
    }

    public Mono<Path> downloadLauncher(
        Path outputDir, String minecraftVersion, String loaderVersion, String installerVersion,
        FileDownloadStatusHandler statusHandler,
        boolean skipValidation
    ) {
        return sharedFetch.fetch(
                uriBuilder.resolve(
                    "/v2/versions/loader/{game_version}/{loader_version}/{installer_version}/server/jar",
                    minecraftVersion, loaderVersion, installerVersion
                )
            )
            .toDirectory(outputDir)
            .handleStatus(statusHandler)
            .assemble()
            .retryWhen(Retry.backoff(downloadRetryMaxAttempts, downloadRetryMinBackoff).filter(IOException.class::isInstance))
            .flatMap(path -> skipValidation ?
                Mono.just(path)
                : validateLauncherJar(path).subscribeOn(Schedulers.boundedElastic())
            )
            .doOnError(InvalidContentException.class, e ->
                log.warn("Invalid launcher jar, will try again: {}", e.getMessage())
            )
            .retryWhen(Retry.backoff(downloadRetryMaxAttempts, downloadRetryMinBackoff).filter(InvalidContentException.class::isInstance))
            .checkpoint("downloadLauncher");
    }

    private Mono<Path> validateLauncherJar(Path path) {
        return Mono.create(sink -> {
            log.debug("Validating Fabric launcher file {}", path);

            if (!path.toFile().isFile()) {
                sink.error(new InvalidContentException("Downloaded launcher jar is not a file"));
            }
            try {
                final Properties installProperties = IoStreams.readFileFromZip(path, "install.properties", in -> {
                        Properties p = new Properties();
                        p.load(in);
                        return p;
                    }
                );
                if (installProperties == null) {
                    debugDownloadedContent(path);
                    sink.error(new InvalidContentException("Does not contain an install.properties"));
                }
                else if (!installProperties.containsKey("game-version")) {
                    debugDownloadedContent(path);
                    sink.error(new InvalidContentException("Does not contain a valid install.properties"));
                }
            } catch (IOException e) {
                debugDownloadedContent(path);
                sink.error(new InvalidContentException("Could not be read as a jar/zip", e));
            }

            sink.success(path);
        });
    }

    private static void debugDownloadedContent(Path path) {
        if (log.isDebugEnabled()) {
            try (InputStream in = Files.newInputStream(path)) {
                final byte[] buf = new byte[100];
                final int amount = in.read(buf);

                log.debug("Downloaded launcher jar content starts with: {}",
                    Hex.encodeHexString(ByteBuffer.wrap(buf, 0, amount))
                );
            } catch (IOException|IndexOutOfBoundsException e) {
                log.warn("Failed to debug content of launcher jar", e);
            }
        }
    }

    @NotNull
    private static Mono<String> findFirst(List<VersionEntry> versionEntries, Predicate<VersionEntry> condition
    ) {
        return Mono.justOrEmpty(
            versionEntries.stream()
                .filter(condition)
                .map(VersionEntry::getVersion)
                .findFirst()
        );
    }
}
