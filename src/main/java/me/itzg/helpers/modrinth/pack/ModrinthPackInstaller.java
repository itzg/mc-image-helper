package me.itzg.helpers.modrinth.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.*;
import me.itzg.helpers.modrinth.model.*;
import me.itzg.helpers.quilt.QuiltInstaller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ModrinthPackInstaller {
    private final ModrinthApiClient apiClient;
    private final ModrinthPack.Config config;
    private final Path zipFile;

    public ModrinthPackInstaller(ModrinthPack.Config config, Path zipFile) {
        this.apiClient = new ModrinthApiClient(
            config.apiBaseUrl, "install-modrinth-modpack",
            config.sharedFetchArgs.options());;
        this.config = config;
        this.zipFile = zipFile;
    }

    public Mono<ModpackIndex> processModpack() {
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

        return processModpackFiles(apiClient, modpackIndex)
            .collectList()
            .map(modFiles ->
                Stream.of(
                        modFiles.stream(),
                        extractOverrides(
                            zipFile, "overrides", "server-overrides")
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

                return Mono.just(modpackIndex);
            });
    }

    private Flux<Path> processModpackFiles(
            ModrinthApiClient apiClient, ModpackIndex modpackIndex)
    {
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
                    this.config.outputDirectory.resolve(modpackFile.getPath());
                try {
                    //noinspection BlockingMethodInNonBlockingContext
                    Files.createDirectories(outFilePath.getParent());
                } catch (IOException e) {
                    return Mono.error(new GenericException(
                        "Failed to created directory for file to download", e));
                }

                return apiClient.downloadFileFromUrl(
                    outFilePath,
                    modpackFile.getDownloads().get(0),
                    (uri, file, contentSizeBytes) ->
                        log.info("Downloaded {}", modpackFile.getPath())
                );
            });
    }

    @SuppressWarnings("SameParameterValue")
    private Stream<Path> extractOverrides(Path zipFile, String... overridesDirs) {
        return Stream.of(overridesDirs)
            .flatMap(dir ->
            {
                final String prefix = dir + "/";
                final List<Path> extracted = new ArrayList<>();
                try (ZipInputStream zipIn = 
                    new ZipInputStream(Files.newInputStream(zipFile))) {
                    ZipEntry entry;
                    while ((entry = zipIn.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            if (entry.getName().startsWith(prefix)) {
                                final Path outFile = this.config.outputDirectory.resolve(
                                    entry.getName().substring(prefix.length())
                                );
                                Files.createDirectories(outFile.getParent());

                                try {
                                    Files.copy(zipIn, outFile, StandardCopyOption.REPLACE_EXISTING);
                                    extracted.add(outFile);
                                } catch (IOException e) {
                                    throw new GenericException(
                                        String.format("Failed to extract %s from overrides", entry.getName()), e
                                    );
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new GenericException("Failed to extract overrides", e);
                }
                return extracted.stream();
            });
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
                this.config.outputDirectory,
                this.config.resultsFile,
                this.config.forceModloaderReinstall,
                null
            );
            return;
        }

        final String fabricVersion = dependencies.get(DependencyId.fabricLoader);
        if (fabricVersion != null) {
            new FabricLauncherInstaller(this.config.outputDirectory)
                .setResultsFile(this.config.resultsFile)
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
                    this.config.sharedFetchArgs.options(),
                    this.config.outputDirectory,
                    minecraftVersion)
                .setResultsFile(this.config.resultsFile)) {

                installer.installWithVersion(null, quiltVersion);
            }
        }

    }

}
