package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.forge.ForgeInstallerResolver;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.quilt.QuiltInstaller;
import org.jetbrains.annotations.Blocking;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ModrinthPackInstaller {
    private final ModrinthApiClient apiClient;
    private final Path zipFile;
    private final Path outputDirectory;
    private final Path resultsFile;
    private final boolean forceModloaderReinstall;
    private final Options sharedFetchOpts;

    @Setter
    private List<String> excludeFiles;

    public ModrinthPackInstaller(
            ModrinthApiClient apiClient, Options sharedFetchOpts,
            Path zipFile, Path outputDirectory, Path resultsFile,
            boolean forceModloaderReinstall)
    {
        this.apiClient = apiClient;
        this.sharedFetchOpts = sharedFetchOpts;
        this.zipFile = zipFile;
        this.outputDirectory = outputDirectory;
        this.resultsFile = resultsFile;
        this.forceModloaderReinstall = forceModloaderReinstall;
    }

    public Mono<Installation> processModpack(SharedFetch sharedFetch) {
        final ModpackIndex modpackIndex;
        try {
            modpackIndex = IoStreams.readFileFromZip(
                this.zipFile, "modrinth.index.json", in ->
                    ObjectMappers.defaultMapper().readValue(in, ModpackIndex.class)
            );
        } catch (IOException e) {
            return Mono.error(
                new GenericException("Failed to read modpack index", e));
        }

        if (modpackIndex == null) {
            return Mono.error(
                new InvalidParameterException(
                    "Modpack is missing modrinth.index.json")
            );
        }

        if (!Objects.equals("minecraft", modpackIndex.getGame())) {
            return Mono.error(
                new InvalidParameterException(
                    "Requested modpack is not for minecraft: " + modpackIndex.getGame()
                ));
        }

        return processModpackFiles(modpackIndex)
            .collectList()
            .map(modFiles ->
                Stream.of(
                        modFiles.stream(),
                        extractOverrides("overrides", "server-overrides")
                    )
                    .flatMap(Function.identity())
                    .collect(Collectors.toList())
            )
            .flatMap(paths -> Mono.fromCallable(() -> {
                    applyModLoader(sharedFetch, modpackIndex.getDependencies());

                    return new Installation()
                        .setIndex(modpackIndex)
                        .setFiles(paths);
                }).subscribeOn(Schedulers.boundedElastic())
            );
    }

    private Flux<Path> processModpackFiles(ModpackIndex modpackIndex) {
        return Flux.fromStream(modpackIndex.getFiles().stream()
                .filter(modpackFile ->
                    // env is optional
                    modpackFile.getEnv() == null
                        || modpackFile.getEnv()
                            .get(Env.server) != EnvType.unsupported
                )
            )
            .publishOn(Schedulers.boundedElastic())
            .flatMap(modpackFile -> {
                final String modpackFilePath = sanitizeModFilePath(modpackFile.getPath());
                if (shouldExcludeFile(modpackFilePath)) {
                    return Mono.empty();
                }

                final Path outFilePath =
                    this.outputDirectory.resolve(modpackFilePath);
                try {
                    //noinspection BlockingMethodInNonBlockingContext
                    Files.createDirectories(outFilePath.getParent());
                } catch (IOException e) {
                    return Mono.error(new GenericException(
                        "Failed to created directory for file to download", e));
                }

                return this.apiClient.downloadFileFromUrl(
                    outFilePath,
                    modpackFile.getDownloads().get(0),
                    (uri, file, contentSizeBytes) ->
                        log.info("Downloaded {}", modpackFilePath)
                );
            });
    }

    private boolean shouldExcludeFile(String modpackFilePath) {
        if (excludeFiles == null || excludeFiles.isEmpty()) {
            return false;
        }

        // to match case-insensitive
        final String normalized = modpackFilePath.toLowerCase();

        final boolean exclude = excludeFiles.stream()
            .anyMatch(s -> normalized.contains(s.toLowerCase()));
        if (exclude) {
            log.debug("Excluding '{}' as requested", modpackFilePath);
        }
        return exclude;
    }

    private String sanitizeModFilePath(String path) {
        // Using only backslash delimiters and not forward slashes?
        // (mixed usage will assume backslashes were purposeful)
        if (path.contains("\\") && !path.contains("/")) {
            return path.replace("\\", "/");
        }
        else {
            return path;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private Stream<Path> extractOverrides(String... overridesDirs) {
        try (ZipFile zipFileReader = new ZipFile(zipFile.toFile())) {
            return Stream.of(overridesDirs)
                .flatMap(dir -> {
                    final String prefix = dir + "/";
                    return zipFileReader.stream()
                        .filter(entry -> !entry.isDirectory()
                            && entry.getName().startsWith(prefix)
                        )
                        .map(entry -> {
                            final Path outFile = outputDirectory.resolve(
                                entry.getName().substring(prefix.length())
                            );

                            try {
                                Files.createDirectories(outFile.getParent());
                                Files.copy(zipFileReader.getInputStream(entry), outFile, StandardCopyOption.REPLACE_EXISTING);
                                return outFile;
                            } catch (IOException e) {
                                throw new GenericException(
                                    String.format("Failed to extract %s from overrides", entry.getName()), e
                                );
                            }
                        });
                })
                // need to eager load the stream while the zip file is open
                .collect(Collectors.toList())
                .stream();
        } catch (IOException e) {
            throw new GenericException("Failed to extract overrides", e);
        }
    }

    @Blocking
    private void applyModLoader(SharedFetch sharedFetch, Map<DependencyId, String> dependencies) {
        log.debug("Applying mod loader from dependencies={}", dependencies);

        final String minecraftVersion = dependencies.get(DependencyId.minecraft);
        if (minecraftVersion == null) {
            throw new GenericException(
                "Modpack dependencies missing minecraft version: " + dependencies);
        }

        final String forgeVersion = dependencies.get(DependencyId.forge);
        if (forgeVersion != null) {
            new ForgeInstaller(
                new ForgeInstallerResolver(sharedFetch, minecraftVersion, forgeVersion)
            )
                .install(
                    this.outputDirectory,
                    this.resultsFile,
                    this.forceModloaderReinstall,
                    "Forge"
                );
            return;
        }

        final String fabricVersion = dependencies.get(DependencyId.fabricLoader);
        if (fabricVersion != null) {
            new FabricLauncherInstaller(this.outputDirectory)
                .setResultsFile(this.resultsFile)
                .installUsingVersions(
                    minecraftVersion,
                    fabricVersion,
                    null
                );
            return;
        }

        final String quiltVersion = dependencies.get(DependencyId.quiltLoader);
        if (quiltVersion != null) {
            try (QuiltInstaller installer =
                new QuiltInstaller(QuiltInstaller.DEFAULT_REPO_URL,
                    this.sharedFetchOpts,
                    this.outputDirectory,
                    minecraftVersion)
                .setResultsFile(this.resultsFile)) {

                installer.installWithVersion(null, quiltVersion);
            }
        }
    }

}
