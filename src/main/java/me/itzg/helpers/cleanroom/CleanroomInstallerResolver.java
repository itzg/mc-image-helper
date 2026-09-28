package me.itzg.helpers.cleanroom;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.Uris;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;

@Slf4j
public class CleanroomInstallerResolver {

    public static final String LATEST = "latest";
    public static final String DEFAULT_RELEASE_URL = "https://api.github.com/repos/CleanroomMC/Installer/releases";

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

    private final String releaseUrl;
    private final SharedFetch sharedFetch;
    private final String requestedInstallerVersion;
    private final String requestedCleanroomVersion;

    public CleanroomInstallerResolver(SharedFetch sharedFetch,
                             String requestedInstallerVersion,
                             String requestedCleanroomVersion,
                             String releaseUrl
    ) {
        this.sharedFetch = sharedFetch;
        this.requestedInstallerVersion = requestedInstallerVersion;
        this.requestedCleanroomVersion = requestedCleanroomVersion;
        this.releaseUrl = releaseUrl;
    }

    public CleanroomVersion resolve(CleanroomManifest prevManifest, String expectedVersion) {
        if (prevManifest != null) {
            final String prevInstallerVersion = prevManifest.getInstallerVersion();
            if (prevInstallerVersion.equals(requestedInstallerVersion)) {
                log.debug("Resolved Cleanroom installer {} from previous manifest", prevInstallerVersion);
                return new CleanroomVersion(requestedInstallerVersion, requestedCleanroomVersion);
            }
        }

        return resolveInstallerVersion(requestedInstallerVersion, expectedVersion);
    }

    public Path download(CleanroomVersion cleanroomVersion, Path outputDir) {
        log.info("Downloading Cleanroom installer {}", cleanroomVersion);

        final Path installerJar = outputDir.resolve(String.format("installer-%s",
            cleanroomVersion.installerVersion()
        ) + ".jar");

        final Path result = sharedFetch.fetch(Uris.populateToUri(
                releaseUrl
                    + "/{version}/installer-{version}.jar",
                cleanroomVersion.installerVersion(), cleanroomVersion.installerVersion()
            ))
            .toFile(installerJar)
            .skipExisting(true)
            .acceptContentTypes(Collections.singletonList("application/java-archive"))
            .assemble()
            .checkpoint("downloading installer jar for " + cleanroomVersion.installerVersion())
            // just skip this one if not found
            .onErrorComplete(FailedRequestException::isNotFound)
            .block();

        if (result == null) {
            throw new GenericException(String.format(
                "Failed to locate cleanroom installer  %s", cleanroomVersion.installerVersion()
            ));
        }
        else {
            return result;
        }
    }

    public void cleanup(Path installerJar) {
        try {
            Files.delete(installerJar);
        } catch (IOException e) {
            log.warn("Failed to delete installer jar {}", installerJar);
        }
    }

    public String getDescription() {
        return String.format("Cleanroom installer %s (Loader version: %s)", requestedInstallerVersion, requestedCleanroomVersion);
    }

    private CleanroomVersion resolveInstallerVersion(String installerVersion, String expected) {
        final String version = installerVersion.toLowerCase();
        if (!version.equals(LATEST) && expected == null) {
            return new CleanroomVersion(installerVersion, requestedCleanroomVersion);
        }

        installerVersion = sharedFetch.fetch(URI.create(releaseUrl))
            .userAgentCommand("CleanroomMCAgent")
            .toObjectList(CleanroomInstallerEntry.class)
            .assemble()
            .flatMap(installerEntries -> installerEntries.stream()
                .filter(installerEntry -> version.equals(LATEST) || installerEntry.getName().equals(expected))
                .findFirst()
                .map(installerEntry -> Mono.just(installerEntry.getName()))
                .orElseGet(
                    () -> Mono.error(new GenericException("Failed to find installer from " + releaseUrl))
                )
            )
            .retryWhen(Retry.backoff(retryMaxAttempts, retryMinBackoff).filter(IOException.class::isInstance))
            .block();

        return new CleanroomVersion(installerVersion, requestedCleanroomVersion);
    }
}
