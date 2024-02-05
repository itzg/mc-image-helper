package me.itzg.helpers.fabric;

import static me.itzg.helpers.http.Fetch.sharedFetch;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@Slf4j
public class FabricLauncherInstaller {

    @NonNull
    private final Path outputDir;

    @Setter
    private Path resultsFile;

    @Getter @Setter
    private String fabricMetaBaseUrl = "https://meta.fabricmc.net";

    @Getter @Setter
    private boolean forceReinstall;

    public void installUsingVersions(
        @NonNull String minecraftVersion,
        @Nullable String loaderVersion,
        @Nullable String installerVersion
    ) {
        try (SharedFetch sharedFetch = sharedFetch("fabric", Options.builder().build())) {
            final FabricMetaClient fabricMetaClient = new FabricMetaClient(sharedFetch, fabricMetaBaseUrl);

            fabricMetaClient.resolveMinecraftVersion(minecraftVersion)
                .doOnNext(v -> log.debug("Resolved minecraft version {} from {}", v, minecraftVersion))
                .flatMap(resolvedMinecraftVersion ->
                    fabricMetaClient.resolveLoaderVersion(resolvedMinecraftVersion, loaderVersion)
                        .doOnNext(v -> log.debug("Resolved loader version {} from {}", v, loaderVersion))
                        .flatMap(resolvedLoaderVersion ->

                            fabricMetaClient.resolveInstallerVersion(installerVersion)
                                .doOnNext(v -> log.debug("Resolved installer version {} from {}", v, installerVersion))
                                .flatMap(resolvedInstallerVersion -> downloadResolvedLauncher(
                                    fabricMetaClient,
                                    resolvedMinecraftVersion,
                                    resolvedLoaderVersion,
                                    resolvedInstallerVersion
                                ))
                        )
                )
                .block();
        }


    }

    private Mono<FabricManifest> downloadResolvedLauncher(FabricMetaClient fabricMetaClient,
        String minecraftVersion, String loaderVersion, String installerVersion
    ) {
        final FabricManifest prevManifest = Manifests.load(outputDir, FabricManifest.MANIFEST_ID,
            FabricManifest.class
        );

        final Versions expectedVersions = Versions.builder()
            .game(minecraftVersion)
            .loader(loaderVersion)
            .installer(installerVersion)
            .build();

        final boolean needsInstall =
            prevManifest == null
                || forceReinstall
                || prevManifest.getOrigin() == null
                || !prevManifest.getOrigin().equals(expectedVersions)
                || !Manifests.allFilesPresent(outputDir, prevManifest);

        if (needsInstall) {
            return fabricMetaClient.downloadLauncher(
                    outputDir, minecraftVersion, loaderVersion, installerVersion,
                    Fetch.loggingDownloadStatusHandler(log)
                )
                .publishOn(Schedulers.boundedElastic())
                .flatMap(launcherPath ->
                    {
                        try {
                            //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
                            return finalizeResultsFileAndManifest(
                                prevManifest,
                                expectedVersions,
                                launcherPath
                            );
                        } catch (IOException e) {
                            return Mono.error(
                                new GenericException("Failed to finalize Fabric setup", e)
                            );
                        }
                    }

                );
        }
        else {
            // For backward compatibility, need to re-write the results file
            if (resultsFile != null) {
                try {
                    writeResultsFile(Paths.get(prevManifest.getLauncherPath()), minecraftVersion);
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to re-write results file", e));
                }
            }
            log.info("Fabric launcher for minecraft {} loader {} is already available",
                minecraftVersion, loaderVersion
            );
            return Mono.empty();
        }
    }

    @Blocking
    private Mono<FabricManifest> finalizeResultsFileAndManifest(FabricManifest prevManifest, Versions versions, Path launcherPath)
        throws IOException {
        if (resultsFile != null) {
            writeResultsFile(launcherPath, versions.getGame());
        }

        final FabricManifest newManifest = FabricManifest.builder()
            .origin(versions)
            .files(
                Manifests.relativizeAll(outputDir, launcherPath)
            )
            .launcherPath(launcherPath.toString())
            .build();

        Manifests.cleanup(outputDir, prevManifest, newManifest, log);

        Manifests.save(outputDir, FabricManifest.MANIFEST_ID, newManifest);

        return Mono.just(newManifest);
    }

    public void installUsingUri(URI loaderUri) throws IOException {
        final Path launcherPath;
        try (SharedFetch sharedFetch = sharedFetch("fabric", Options.builder().build())) {
            launcherPath = sharedFetch.fetch(loaderUri)
                .toDirectory(outputDir)
                .skipUpToDate(true)
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .assemble()
                .block();
        }

        if (launcherPath == null) {
            throw new GenericException("Failed to download Fabric launcher");
        }

        if (resultsFile != null) {
            writeResultsFileFromLauncher(launcherPath);
        }

        final FabricManifest prevManifest = Manifests.load(outputDir, FabricManifest.MANIFEST_ID, FabricManifest.class);

        final FabricManifest newManifest = FabricManifest.builder()
            .origin(
                RemoteFile.builder()
                    .uri(loaderUri.toString())
                    .build()
            )
            .files(
                Manifests.relativizeAll(outputDir, launcherPath)
            )
            .launcherPath(launcherPath.toString())
            .build();

        Manifests.save(outputDir, FabricManifest.MANIFEST_ID, newManifest);
        Manifests.cleanup(outputDir, prevManifest, newManifest, log);
    }

    public void installUsingLocalFile(Path launcherFile) throws IOException {
        if (!Files.exists(launcherFile)) {
            throw new InvalidParameterException("The local Fabric launcher file does not exist: " + launcherFile);
        }

        if (resultsFile != null) {
            writeResultsFileFromLauncher(launcherFile);
        }

        Manifests.save(outputDir, FabricManifest.MANIFEST_ID,
            FabricManifest.builder()
                .origin(LocalFile.builder().build())
                .launcherPath(launcherFile.toString())
                .build()
            );
    }

    private void writeResultsFileFromLauncher(Path launcherPath) throws IOException {
        final Properties installProps = IoStreams.readFileFromZip(launcherPath,
            "install.properties", in -> {
                final Properties p = new Properties();
                p.load(in);
                return p;
            }
        );

        if (installProps == null) {
            throw new GenericException("Failed to locate install.properties from launcher " + launcherPath);
        }

        if (!installProps.containsKey("game-version")) {
            throw new GenericException("Install properties from launcher " + launcherPath + " is missing game-version");
        }

        writeResultsFile(launcherPath, installProps.getProperty("game-version"));
    }

    private void writeResultsFile(Path launcherPath, String gameVersion) throws IOException {
        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.writeServer(launcherPath);
            results.write("FAMILY", "FABRIC");
            results.writeType("FABRIC");
            results.writeVersion(gameVersion);
        }
    }
}
