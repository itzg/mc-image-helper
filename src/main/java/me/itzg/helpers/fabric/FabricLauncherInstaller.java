package me.itzg.helpers.fabric;

import static me.itzg.helpers.http.Fetch.fetch;
import static me.itzg.helpers.http.Fetch.sharedFetch;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.fabric.LoaderResponseEntry.Loader;
import me.itzg.helpers.files.Checksums;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.http.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@Slf4j
public class FabricLauncherInstaller {

    private static final String RESULT_LAUNCHER = "SERVER";
    public static final String MANIFEST_ID = "fabric";
    private static final long FABRIC_INSTALLER_TIMEOUT_SEC = 60;

    private final Path outputDir;
    private final Path resultsFile;

    @Getter
    @Setter
    private String fabricMetaBaseUrl = "https://meta.fabricmc.net";

    /**
     * @param minecraftVersion required
     * @param loaderVersion    optional
     * @param installerVersion optional
     * @return the launcher's path
     */
    public Path installUsingVersions(@NonNull String minecraftVersion, String loaderVersion, String installerVersion)
        throws IOException {
        Objects.requireNonNull(outputDir, "outputDir is required");

        final UriBuilder uriBuilder = UriBuilder.withBaseUrl(fabricMetaBaseUrl);

        try (SharedFetch sharedFetch = sharedFetch("fabric", Options.builder().build())) {
            loaderVersion = resolveLoaderVersion(uriBuilder, sharedFetch, minecraftVersion, loaderVersion);

            installerVersion = resolveInstallerVersion(uriBuilder, sharedFetch, installerVersion);

            final FabricManifest manifest = Manifests.load(outputDir, MANIFEST_ID, FabricManifest.class);

            final Versions versions = Versions.builder()
                .game(minecraftVersion)
                .loader(loaderVersion)
                .installer(installerVersion)
                .build();

            final boolean needsInstall = manifest == null || manifest.getOrigin() == null ||
                !manifest.getOrigin().equals(versions);

            if (needsInstall) {
                return processInstallUsingVersions(minecraftVersion, loaderVersion, installerVersion, uriBuilder, sharedFetch,
                    manifest, versions
                );
            }
            else {
                return Paths.get(manifest.getLauncherPath());
            }

        }

    }

    private Path processInstallUsingVersions(String minecraftVersion, String loaderVersion, String installerVersion,
        UriBuilder uriBuilder,
        SharedFetch sharedFetch, FabricManifest manifest, Versions versions
    ) throws IOException {
        if (manifest != null && manifest.getOrigin() != null) {
            log.info("Upgrading Fabric from {} to {}", manifest.getOrigin(), versions);
        }
        else {
            log.info("Installing Fabric {}", versions);
        }

        final Path launcherPath = sharedFetch.fetch(
                uriBuilder.resolve(
                    "/v2/versions/loader/{game_version}/{loader_version}/{installer_version}/server/jar",
                    minecraftVersion, loaderVersion, installerVersion
                )
            )
            .toDirectory(outputDir)
            .assemble()
            .onErrorResume(FailedRequestException::isBadRequest,
                throwable -> {
                log.debug("Loader could not be downloaded for minecraft={} loader={} installer={}. Getting and using installer instead.",
                    minecraftVersion, loaderVersion, installerVersion);
                    // retrieve and run the installer to obtain the launcher
                    return installLauncherUsingInstaller(sharedFetch, minecraftVersion, loaderVersion,
                        installerVersion
                    );
                }
            )
            .block();

        if (launcherPath == null) {
            throw new GenericException("Unable to resolve launcher path");
        }

        if (resultsFile != null) {
            try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
                results.write(RESULT_LAUNCHER, launcherPath.toString());
                results.write("FAMILY", "FABRIC");
            }
        }

        final FabricManifest newManifest = FabricManifest.builder()
            .origin(versions)
            .files(
                Manifests.relativizeAll(outputDir, Collections.singletonList(launcherPath))
            )
            .launcherPath(launcherPath.toString())
            .build();

        Manifests.cleanup(outputDir, manifest, newManifest, log);

        Manifests.save(outputDir, MANIFEST_ID, newManifest);

        return launcherPath;
    }

    private Mono<Path> installLauncherUsingInstaller(SharedFetch sharedFetch,
        String minecraftVersion, String loaderVersion, String installerVersion
    ) {
        final Path installerFile = outputDir.resolve(String.format("fabric-installer-%s.jar", installerVersion));

        final URI installerUri = UriBuilder.withNoBaseUrl()
            .resolve("https://maven.fabricmc.net/net/fabricmc/fabric-installer/{version}/fabric-installer-{version}.jar",
                installerVersion, installerVersion
            );

        if (log.isDebugEnabled()) {
            log.debug("Downloading Fabric installer from {}", installerUri);
        }
        else {
            log.info("Downloading Fabric installer");
        }

        return sharedFetch.fetch(
                installerUri
            )
            .toFile(installerFile)
            .skipExisting(true)
            .assemble()
            .publishOn(Schedulers.boundedElastic())
            .map(path -> {
                log.info("Running Fabric installer for Minecraft {} and loader version {}",
                    minecraftVersion, loaderVersion
                );

                try {
                    final Process proc = new ProcessBuilder(
                        "java", "-jar", path.toString(),
                        "server",
                        "-mcversion", minecraftVersion,
                        "-loader", loaderVersion
                    )
                        .directory(outputDir.toFile())
                        .redirectErrorStream(true)
                        .start();

                    final boolean success = proc.waitFor(FABRIC_INSTALLER_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (!success) {
                        IoStreams.transfer(proc.getInputStream(), System.err);
                        throw new GenericException("Fabric installer took too long to run");
                    }

                    if (proc.exitValue() != 0) {
                        IoStreams.transfer(proc.getInputStream(), System.err);
                        throw new GenericException("Fabric installer failed to run");
                    }
                } catch (IOException e) {
                    throw new GenericException("Failed to run fabric installer", e);
                } catch (InterruptedException e) {
                    throw new GenericException("While running fabric installer", e);
                } finally {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete fabric installer at {}", path, e);
                    }
                }

                return outputDir.resolve("fabric-server-launch.jar");
            });
    }

    public void installGivenLauncherFile(Path launcher) throws IOException {
        if (resultsFile != null) {
            try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
                results.write(RESULT_LAUNCHER, launcher.toString());
                results.write("FAMILY", "FABRIC");
            }
        }

        if (outputDir != null) {
            // Do some manifest updates

            final FabricManifest manifest = Manifests.load(outputDir, MANIFEST_ID, FabricManifest.class);
            final String previousChecksum;
            if (manifest != null && manifest.getOrigin() instanceof LocalFile) {
                previousChecksum = ((LocalFile) manifest.getOrigin()).getChecksum();
            }
            else {
                previousChecksum = null;
            }

            final String newChecksum = Checksums.checksumLike(previousChecksum, launcher);
            final FabricManifest newManifest = FabricManifest.builder()
                .origin(LocalFile.builder()
                    .checksum(newChecksum)
                    .build())
                .launcherPath(launcher.toString())
                .build();

            if (previousChecksum != null) {
                if (!previousChecksum.equals(newChecksum)) {
                    log.info("Provided launcher has changed");
                }
            }
            else if (manifest != null) {
                Manifests.cleanup(outputDir, manifest, newManifest, log);

                if (manifest.getOrigin() != null) {
                    log.info("Switching from {} to provided launcher", manifest.getOrigin());
                }
            }

            Manifests.save(outputDir, MANIFEST_ID, newManifest);
        }
    }

    /**
     * @return launcher's path
     */
    public Path installUsingUri(URI loaderUri) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir is required");

        final Path launcher;
        try {
            launcher = fetch(loaderUri)
                .toDirectory(outputDir)
                .skipExisting(true)
                .execute();
        } catch (IOException e) {
            throw new GenericException("Failed to fetch launcher from " + loaderUri, e);
        }

        final RemoteFile newOrigin = RemoteFile.builder()
            .uri(loaderUri.toString())
            .build();

        final FabricManifest oldManifest = Manifests.load(outputDir, MANIFEST_ID, FabricManifest.class);
        if (oldManifest != null) {
            if (!Objects.equals(oldManifest.getOrigin(), newOrigin)) {
                log.info("Switching from {} to {} downloaded from {}",
                    oldManifest.getOrigin(), launcher, loaderUri
                );
            }
        }
        else {
            log.info("Using {} downloaded from {}", launcher, loaderUri);
        }

        final FabricManifest newManifest = FabricManifest.builder()
            .origin(newOrigin)
            .launcherPath(launcher.toString())
            .files(Manifests.relativizeAll(outputDir, Collections.singletonList(launcher)))
            .build();
        Manifests.save(outputDir, MANIFEST_ID, newManifest);

        Manifests.cleanup(outputDir, oldManifest, newManifest, log);

        if (resultsFile != null) {
            try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
                results.write(RESULT_LAUNCHER, launcher.toString());
                results.write("FAMILY", "FABRIC");
            } catch (IOException e) {
                throw new GenericException("Failed to write results file", e);
            }
        }

        return launcher;
    }

    private String resolveInstallerVersion(UriBuilder uriBuilder, SharedFetch sharedFetch, String installerVersion) {
        if (nonEmptyString(installerVersion)) {
            return installerVersion;
        }

        try {
            final List<InstallerEntry> installerEntries = sharedFetch.fetch(uriBuilder.resolve("/v2/versions/installer"))
                .toObjectList(InstallerEntry.class)
                .execute();

            return installerEntries.stream()
                .filter(InstallerEntry::isStable)
                .findFirst()
                .orElseThrow(() -> new GenericException("Failed to find stable installer from " + fabricMetaBaseUrl))
                .getVersion();
        } catch (IOException e) {
            throw new GenericException("Failed to retrieve installer metadata from " + fabricMetaBaseUrl, e);
        }
    }

    private String resolveLoaderVersion(UriBuilder uriBuilder, SharedFetch sharedFetch, String minecraftVersion,
        String loaderVersion
    ) {
        if (nonEmptyString(loaderVersion)) {
            return loaderVersion;
        }

        final List<LoaderResponseEntry> loaderResponse;
        try {
            loaderResponse = sharedFetch.fetch(
                    uriBuilder.resolve("/v2/versions/loader/{game_version}", minecraftVersion))
                .toObjectList(LoaderResponseEntry.class)
                .execute();
        } catch (IOException e) {
            throw new GenericException("Failed to retrieve loader metadata from " + fabricMetaBaseUrl, e);
        }

        if (loaderResponse.isEmpty()) {
            throw new GenericException("No loader entries provided from " + fabricMetaBaseUrl);
        }

        final Loader loader = loaderResponse.stream()
            .filter(entry -> entry.getLoader() != null && entry.getLoader().isStable())
            .findFirst()
            .orElseThrow(() -> new GenericException("No stable loaders found from " + fabricMetaBaseUrl))
            .getLoader();

        return loader.getVersion();
    }

    private static boolean nonEmptyString(String loaderVersion) {
        return loaderVersion != null && !loaderVersion.isEmpty();
    }

}
