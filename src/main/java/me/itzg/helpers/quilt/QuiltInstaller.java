package me.itzg.helpers.quilt;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.mvn.MavenRepoApi;
import me.itzg.helpers.versions.MinecraftVersionsApi;
import org.jetbrains.annotations.Blocking;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class QuiltInstaller implements AutoCloseable {

    public static final String LOADER_ARTIFACT_ID = "quilt-loader";
    public static final String INSTALLER_ARTIFACT = "quilt-installer";
    public static final String QUILT_GROUP_ID = "org.quiltmc";
    public static final String DEFAULT_REPO_URL = "https://maven.quiltmc.org/repository/release";

    private final MavenRepoApi mavenRepoApi;
    private final Path outputDir;
    private final String minecraftVersion;
    private final MinecraftVersionsApi minecraftVersionsApi;
    private final SharedFetch sharedFetch;

    @Setter
    private Path resultsFile;

    @Setter
    private boolean forceReinstall;

    public QuiltInstaller(String repoUrl, SharedFetch.Options fetchOptions, Path outputDir, String minecraftVersion) {

        this.outputDir = requireNonNull(outputDir, "Missing outputDir");
        this.minecraftVersion = requireNonNull(minecraftVersion, "Missing minecraftVersion");
        sharedFetch = Fetch.sharedFetch("quilt-installer", fetchOptions);
        mavenRepoApi = new MavenRepoApi(repoUrl, sharedFetch);
        minecraftVersionsApi = new MinecraftVersionsApi(sharedFetch);
    }

    /**
     * @param installerVersion specific version or null to use latest
     * @param loaderVersion    specific version or null to use latest
     */
    public void installWithVersion(String installerVersion, String loaderVersion) {
        handleInstall(prevManifest ->
                minecraftVersionsApi.resolve(minecraftVersion)
                    .flatMap(resolvedMinecraftVersion ->
                        resolveLoaderVersion(loaderVersion)
                            .filter(resolvedLoaderVersion ->
                                needsInstall(prevManifest, resolvedMinecraftVersion, resolvedLoaderVersion)
                            )
                            .flatMap(resolvedLoaderVersion ->
                                mavenRepoApi.download(outputDir, QUILT_GROUP_ID, INSTALLER_ARTIFACT,
                                        installerVersion,
                                        "jar", null
                                    )
                                    .switchIfEmpty(
                                        Mono.defer(() -> Mono.error(new GenericException("Unable to obtain Quilt installer"))))
                                    .publishOn(Schedulers.boundedElastic())
                                    .map(installerPath -> runInstaller(resolvedMinecraftVersion, installerPath, resolvedLoaderVersion))
                            )
                    )
            );
    }

    public void installFromUrl(URI installerUrl, String loaderVersion) {
        handleInstall(prevManifest ->
            minecraftVersionsApi.resolve(minecraftVersion)
            .flatMap(resolvedMinecraftVersion ->
                resolveLoaderVersion(loaderVersion)
                    .filter(resolvedLoaderVersion ->
                        needsInstall(prevManifest, resolvedMinecraftVersion, resolvedLoaderVersion)
                    )
                    .flatMap(resolvedLoaderVersion ->
                        downloadFromCustomInstallerUrl(installerUrl)
                            .publishOn(Schedulers.boundedElastic())
                            .map(installerPath -> runInstaller(resolvedMinecraftVersion, installerPath, resolvedLoaderVersion))
                    )
            )
        );
    }

    @FunctionalInterface
    private interface ResolveAndInstaller {
        Mono<QuiltManifest> assemble(QuiltManifest prevManifest);
    }

    protected void handleInstall(ResolveAndInstaller resolveAndInstaller) {
        final QuiltManifest prevManifest = Manifests.load(outputDir, QuiltManifest.ID, QuiltManifest.class);

        final QuiltManifest newManifest = resolveAndInstaller.assemble(prevManifest)
            .block();

        if (newManifest != null) {
            try {
                Manifests.cleanup(outputDir, prevManifest, newManifest, log);
            } catch (IOException e) {
                log.error("Failed to cleanup Quilt files", e);
            }
            Manifests.save(outputDir, QuiltManifest.ID, newManifest);
        }
    }

    private Mono<Path> downloadFromCustomInstallerUrl(URI installerUrl) {
        return sharedFetch.fetch(installerUrl)
            .toDirectory(outputDir)
            .skipUpToDate(true)
            .assemble();
    }

    private boolean needsInstall(QuiltManifest prevManifest, String resolvedMinecraftVersion, String resolvedLoaderVersion) {
        if (
            prevManifest != null
                && prevManifest.getMinecraftVersion().equals(resolvedMinecraftVersion)
                && prevManifest.getLoaderVersion().equals(resolvedLoaderVersion)
                && Manifests.allFilesPresent(outputDir, prevManifest)
        ) {
            if (forceReinstall) {
                log.info("Quilt {} loader {} is already installed, but force reinstall requested",
                    resolvedMinecraftVersion, resolvedLoaderVersion
                );
            }
            else {
                log.info("Quilt {} loader {} is already installed",
                    resolvedMinecraftVersion, resolvedLoaderVersion
                );
                return false;
            }
        }
        return true;
    }

    @Blocking
    private QuiltManifest runInstaller(String minecraftVersion, Path installerPath, String resolvedLoaderVersion) {
        log.info("Installing Quilt version {} with loader {}", minecraftVersion, resolvedLoaderVersion);

        try {
            final Process proc = new ProcessBuilder("java", "-jar",
                installerPath.toAbsolutePath().toString(),
                "install", "server",
                minecraftVersion,
                resolvedLoaderVersion,
                "--install-dir=./",
                "--download-server"
            )
                .directory(outputDir.toFile())
                .redirectError(Redirect.INHERIT)
                .start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))
            ) {

                final int exitCode = proc.waitFor();
                if (exitCode != 0) {
                    reader.lines().forEach(System.err::println);
                    throw new GenericException("Quilt installer failed with exit code " + exitCode);
                }
            }
        } catch (IOException e) {
            throw new GenericException("Failed to run Quilt installer at " + installerPath, e);
        } catch (InterruptedException e) {
            throw new GenericException("Interrupted whiel running Quilt installer at " + installerPath, e);
        }

        final Path installedLauncher = outputDir.resolve("quilt-server-launch.jar");
        if (!Files.exists(installedLauncher)) {
            throw new GenericException("Expected launcher file not present: " + installedLauncher);
        }

        final Path resolvedLauncher = outputDir.resolve(String.format("quilt-server-%s-%s-launcher.jar",
            minecraftVersion, resolvedLoaderVersion
        ));

        try {
            Files.move(installedLauncher, resolvedLauncher, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GenericException("Failed to rename server launcher", e);
        }

        if (resultsFile != null) {
            try (ResultsFileWriter resultsWriter = new ResultsFileWriter(resultsFile, false)) {
                resultsWriter.writeServer(resolvedLauncher);
                resultsWriter.write("FAMILY", "FABRIC");
                resultsWriter.writeType("QUILT");
                resultsWriter.writeVersion(minecraftVersion);
            } catch (IOException e) {
                throw new GenericException("Failed to write results file", e);
            }
        }

        return QuiltManifest.builder()
            .minecraftVersion(minecraftVersion)
            .loaderVersion(resolvedLoaderVersion)
            .files(Manifests.relativizeAll(outputDir, resolvedLauncher))
            .build();
    }

    private Mono<String> resolveLoaderVersion(String loaderVersion) {
        if (loaderVersion != null && !loaderVersion.equalsIgnoreCase("latest")) {
            return Mono.just(loaderVersion);
        }

        return mavenRepoApi.fetchMetadata(QUILT_GROUP_ID, LOADER_ARTIFACT_ID)
            .map(mavenMetadata -> mavenMetadata.getVersioning().getLatest());
    }

    @Override
    public void close() {
        sharedFetch.close();
    }
}
