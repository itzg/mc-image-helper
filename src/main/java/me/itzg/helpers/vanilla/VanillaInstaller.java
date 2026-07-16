package me.itzg.helpers.vanilla;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.versions.McVersioning;
import me.itzg.helpers.versions.MinecraftVersionInfo;
import me.itzg.helpers.versions.MinecraftVersionsApi;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class VanillaInstaller {
    private final SharedFetch sharedFetch;
    private final MinecraftVersionsApi versionsApi;

    public VanillaInstaller(SharedFetch sharedFetch) {
        this.sharedFetch = sharedFetch;
        this.versionsApi = new MinecraftVersionsApi(sharedFetch);
    }

    public void install(String version, Path outputDirectory, Path resultsFile, boolean forceReinstall) throws IOException {
        final VanillaManifest prevManifest = Manifests.load(outputDirectory, VanillaManifest.ID, VanillaManifest.class);

        final boolean needsInstall;
        if (forceReinstall || prevManifest == null) {
            needsInstall = true;
        } else if (!Manifests.allFilesPresent(outputDirectory, prevManifest)) {
            needsInstall = true;
            log.warn("Server files for Minecraft are missing. Reinstalling...");
        } else {
            needsInstall = false;
        }

        final VanillaManifest newManifest;
        if (needsInstall) {
            newManifest = versionsApi
                .resolve(version)
                .flatMap(resolved -> installVersion(outputDirectory, resolved))
                .block();
        } else if (prevManifest.getMinecraftVersion().equalsIgnoreCase(version)) {
            newManifest = prevManifest;
            log.info("Minecraft version {} is already installed", prevManifest.getMinecraftVersion());
        } else {
            newManifest = versionsApi
                .resolve(version)
                // skip reinstall when the player is using 'latest' or 'snapshot' and the actual version
                // hasn't changed
                .flatMap(resolved -> {
                    if (resolved.getVersion().equalsIgnoreCase(prevManifest.getMinecraftVersion())) {
                        log.info("Minecraft version {} is already installed", prevManifest.getMinecraftVersion());
                        return Mono.just(prevManifest);
                    }
                    log.info("Reinstalling Minecraft due to version change from {} to {}", prevManifest.getMinecraftVersion(), resolved.getVersion());
                    return installVersion(outputDirectory, resolved);
                })
                .block();
        }

        if (prevManifest != newManifest) {
            Manifests.cleanup(outputDirectory, prevManifest, newManifest, log);
        }

        Manifests.save(outputDirectory, VanillaManifest.ID, newManifest);

        log.debug("Populating results file {}", resultsFile);

    if (resultsFile != null) {
        try (ResultsFileWriter writer = new ResultsFileWriter(resultsFile)) {
            writer.writeType("VANILLA");
            writer.writeServer(outputDirectory.resolve(newManifest.getServerEntry()));
            writer.writeVersion(newManifest.getMinecraftVersion());
        }
     }
    }

    private Mono<VanillaManifest> installVersion(Path outputDirectory, MinecraftVersionInfo version) {
        return versionsApi.getServerJarUrl(version)
            .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("No server jar download available for version " + version.getVersion())))
            .flatMap(url -> sharedFetch
                .fetch(url)
                .toFile(outputDirectory.resolve(String.format("minecraft_server.%s.jar", version.getVersion().replace(' ', '_'))))
                .assemble())
            .flatMap(serverJar -> {
                final List<Path> files = new ArrayList<>();
                files.add(serverJar);
                String serverEntry = outputDirectory.relativize(serverJar).toString();
                if (McVersioning.compare(version.getVersion(), "1.6") < 0) {
                    final Path symlink = outputDirectory.resolve("minecraft_server.jar");
                    try {
                        Files.deleteIfExists(symlink);
                        Files.createSymbolicLink(symlink, outputDirectory.relativize(serverJar));
                    } catch (IOException e) {
                        return Mono.error(e);
                    }
                    files.add(symlink);
                    serverEntry = outputDirectory.relativize(symlink).toString();
                }

                return Mono.just(VanillaManifest.builder()
                    .minecraftVersion(version.getVersion())
                    .serverEntry(serverEntry)
                    .files(Manifests.relativizeAll(outputDirectory, files))
                    .build());
            });
    }
}
