package me.itzg.helpers.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import lombok.Setter;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class FabricMetaClient {

    private final SharedFetch sharedFetch;
    private final UriBuilder uriBuilder;
    @Setter
    private int downloadRetryMaxAttempts = 5;
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
            });
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
            });
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
            );
    }

    public Mono<Path> downloadLauncher(
        Path outputDir, String minecraftVersion, String loaderVersion, String installerVersion,
        FileDownloadStatusHandler statusHandler
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
            .checkpoint("downloadLauncher");
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
