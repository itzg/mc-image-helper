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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.*;
import me.itzg.helpers.quilt.QuiltInstaller;
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
    private final SharedFetchArgs sharedFetchArgs;

    public ModrinthPackInstaller(
            ModrinthApiClient apiClient, SharedFetchArgs sharedFetchArgs,
            Path zipFile, Path outputDirectory, Path resultsFile,
            boolean forceModloaderReinstall)
    {
        this.apiClient = apiClient;
        this.sharedFetchArgs = sharedFetchArgs;
        this.zipFile = zipFile;
        this.outputDirectory = outputDirectory;
        this.resultsFile = resultsFile;
        this.forceModloaderReinstall = forceModloaderReinstall;
    }

    public Mono<Installation> processModpack() {
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
                    "Requested modpack is not for minecraft: " +
                    modpackIndex.getGame()));
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
            .flatMap(paths -> {
                try {
                    applyModLoader(modpackIndex.getDependencies());
                } catch (IOException e) {
                    return Mono.error(
                        new GenericException("Failed to apply mod loader", e));
                }

                return Mono.just(new Installation()
                    .setIndex(modpackIndex)
                    .setFiles(paths));
            });
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
                final Path outFilePath =
                    this.outputDirectory.resolve(modpackFile.getPath());
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
                        log.info("Downloaded {}", modpackFile.getPath())
                );
            });
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

    private void applyModLoader(
            Map<DependencyId, String> dependencies
        ) throws IOException
    {
        log.debug("Applying mod loader from dependencies={}", dependencies);

        final String minecraftVersion = dependencies.get(DependencyId.minecraft);
        if (minecraftVersion == null) {
            throw new GenericException(
                "Modpack dependencies missing minecraft version: " + dependencies);
        }

        final String forgeVersion = dependencies.get(DependencyId.forge);
        if (forgeVersion != null) {
            new ForgeInstaller().install(
                minecraftVersion,
                forgeVersion,
                this.outputDirectory,
                this.resultsFile,
                this.forceModloaderReinstall,
                null
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
                    this.sharedFetchArgs.options(),
                    this.outputDirectory,
                    minecraftVersion)
                .setResultsFile(this.resultsFile)) {

                installer.installWithVersion(null, quiltVersion);
            }
        }
    }

    @Data
    class Installation {
        ModpackIndex index;
        List<Path> files;
    }
}
