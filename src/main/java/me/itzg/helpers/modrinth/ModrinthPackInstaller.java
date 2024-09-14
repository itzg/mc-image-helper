package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.AntPathMatcher;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.forge.ForgeInstallerResolver;
import me.itzg.helpers.forge.NeoForgeInstallerResolver;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import me.itzg.helpers.quilt.QuiltInstaller;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.VisibleForTesting;
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
    private final FileInclusionCalculator fileInclusionCalculator;
    private final Options sharedFetchOpts;

    private AntPathMatcher overridesExclusions;

    @FunctionalInterface
    interface ModloaderPreparer {

        void prepare(SharedFetch sharedFetch, String minecraftVersion, String version);
    }

    private final Map<DependencyId, ModloaderPreparer> modloaderPreparers = new HashMap<>();
    {
        modloaderPreparers.put(DependencyId.forge, this::prepareForge);
        modloaderPreparers.put(DependencyId.neoforge, this::prepareNeoForge);
        modloaderPreparers.put(DependencyId.fabricLoader, this::prepareFabric);
        modloaderPreparers.put(DependencyId.quiltLoader, this::prepareQuilt);
    }

    public ModrinthPackInstaller(
            ModrinthApiClient apiClient, Options sharedFetchOpts,
            Path zipFile, Path outputDirectory, Path resultsFile,
            boolean forceModloaderReinstall,
            FileInclusionCalculator fileInclusionCalculator)
    {
        this.apiClient = apiClient;
        this.sharedFetchOpts = sharedFetchOpts;
        this.zipFile = zipFile;
        this.outputDirectory = outputDirectory;
        this.resultsFile = resultsFile;
        this.forceModloaderReinstall = forceModloaderReinstall;
        this.fileInclusionCalculator = fileInclusionCalculator;
    }

    public ModrinthPackInstaller setOverridesExclusions(List<String> overridesExclusions) {
        this.overridesExclusions = new AntPathMatcher(overridesExclusions);
        return this;
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

        if (log.isDebugEnabled()) {
            debugLogModpackIndex(modpackIndex);
        }

        if (!Objects.equals("minecraft", modpackIndex.getGame())) {
            return Mono.error(
                new InvalidParameterException(
                    "Requested modpack is not for minecraft: " + modpackIndex.getGame()
                ));
        }

        return processModFiles(modpackIndex)
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

    private void debugLogModpackIndex(ModpackIndex modpackIndex) {
        log.debug("Modpack index: name={}, game={}, versionId={}",
            modpackIndex.getName(), modpackIndex.getGame(), modpackIndex.getVersionId()
            );
        for (final ModpackFile file : modpackIndex.getFiles()) {
            log.debug("Modpack file: path={}, env={}", file.getPath(), file.getEnv());
        }
    }

    private Flux<Path> processModFiles(ModpackIndex modpackIndex) {
        return Flux.fromStream(
            modpackIndex.getFiles().stream()
                .filter(fileInclusionCalculator::includeModFile)
            )
            .publishOn(Schedulers.boundedElastic())
            .flatMap(modpackFile -> {
                final String modpackFilePath = FileInclusionCalculator.sanitizeModFilePath(modpackFile.getPath());

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
                            final String subpath = entry.getName().substring(prefix.length());
                            if (overridesExclusions != null && overridesExclusions.matches(subpath)) {
                                log.debug("Excluding file from overrides: {}", subpath);
                                return null;
                            }

                            final Path outFile = outputDirectory.resolve(subpath);

                            try {
                                Files.createDirectories(outFile.getParent());

                                log.trace("Copying from overrides: {}", subpath);
                                Files.copy(zipFileReader.getInputStream(entry), outFile, StandardCopyOption.REPLACE_EXISTING);

                                return outFile;
                            } catch (IOException e) {
                                throw new GenericException(
                                    String.format("Failed to extract %s from overrides", entry.getName()), e
                                );
                            }
                        })
                        .filter(Objects::nonNull);
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

        for (final Entry<DependencyId, ModloaderPreparer> entry : modloaderPreparers.entrySet()) {
            final String version = dependencies.get(entry.getKey());
            if (version != null) {
                entry.getValue().prepare(sharedFetch, minecraftVersion, version);
                return;
            }
        }

        throw new GenericException("Unsupported or missing modloader in dependencies: " + dependencies);
    }

    private void prepareQuilt(SharedFetch sharedFetch, String minecraftVersion, String quiltVersion) {
        try (QuiltInstaller installer =
            new QuiltInstaller(QuiltInstaller.DEFAULT_REPO_URL,
                this.sharedFetchOpts,
                this.outputDirectory,
                minecraftVersion
            )
            .setResultsFile(this.resultsFile)) {

            installer.installWithVersion(null, quiltVersion);
        }
    }

    @VisibleForTesting
    ModrinthPackInstaller modifyModLoaderPreparer(DependencyId modLoaderId, ModloaderPreparer preparer) {
        modloaderPreparers.put(modLoaderId, preparer);
        return this;
    }

    private void prepareFabric(SharedFetch sharedFetch, String minecraftVersion, String fabricVersion) {
        new FabricLauncherInstaller(this.outputDirectory)
            .setResultsFile(this.resultsFile)
            .installUsingVersions(
                minecraftVersion,
                fabricVersion,
                null
            );
    }

    private void prepareForge(SharedFetch sharedFetch, String minecraftVersion, String version) {
        new ForgeInstaller(
            new ForgeInstallerResolver(sharedFetch, minecraftVersion, version)
        )
            .install(
                this.outputDirectory,
                this.resultsFile,
                this.forceModloaderReinstall,
                "Forge"
            );
    }

    private void prepareNeoForge(SharedFetch sharedFetch, String minecraftVersion, String version) {
        new ForgeInstaller(
            new NeoForgeInstallerResolver(sharedFetch, minecraftVersion, version)
        )
            .install(
                this.outputDirectory,
                this.resultsFile,
                this.forceModloaderReinstall,
                "NeoForge"
            );
    }

}
