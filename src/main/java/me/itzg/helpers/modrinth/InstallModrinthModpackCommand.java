package me.itzg.helpers.modrinth;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.*;
import me.itzg.helpers.quilt.QuiltInstaller;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static me.itzg.helpers.modrinth.ModrinthApiClient.pickVersionFile;

@CommandLine.Command(name = "install-modrinth-modpack",
    description = "Supports installation of Modrinth modpacks along with the associated mod loader",
    mixinStandardHelpOptions = true
)
@Slf4j
public class InstallModrinthModpackCommand implements Callable<Integer> {
    private final static Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );

    @Option(names = "--project", required = true,
        description = "One of" +
            "%n- Project ID or slug" +
            "%n- Project page URL" +
            "%n- Project file URL"
    )
    String modpackProject;

    @Option(names = {"--version-id", "--version"},
        description = "Version ID, name, or number from the file's metadata" +
            "%nDefault chooses newest file based on game version, loader, and/or default version type"
    )
    String version;

    @Option(names = "--game-version", description = "Applicable Minecraft version" +
        "%nDefault: (any)")
    String gameVersion;

    @Option(names = "--loader", description = "Valid values: ${COMPLETION-CANDIDATES}" +
        "%nDefault: (any)")
    ModpackLoader loader;

    @Option(names = "--default-version-type", defaultValue = "release", paramLabel = "TYPE",
        description = "Valid values: ${COMPLETION-CANDIDATES}" +
            "%nDefault: ${DEFAULT-VALUE}"
    )
    VersionType defaultVersionType;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--force-synchronize", defaultValue = "${env:MODRINTH_FORCE_SYNCHRONIZE:-false}")
    boolean forceSynchronize;

    @Option(names = "--force-modloader-reinstall", defaultValue = "${env:MODRINTH_FORCE_MODLOADER_REINSTALL:-false}")
    boolean forceModloaderReinstall;

    @Option(names = "--api-base-url", defaultValue = "${env:MODRINTH_API_BASE_URL:-https://api.modrinth.com}",
        description = "Default: ${DEFAULT-VALUE}"
    )
    String baseUrl;

    @CommandLine.ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws IOException {
        final ModrinthModpackManifest prevManifest = Manifests.load(
            outputDirectory, ModrinthModpackManifest.ID, ModrinthModpackManifest.class);

        final ProjectRef projectRef;
        final Matcher m = MODPACK_PAGE_URL.matcher(modpackProject);
        if (m.matches()) {
            final String versionName = m.group("versionName");
            if (versionName != null && version != null) {
                throw new InvalidParameterException("Cannot provide both project file URL and version");
            }
            projectRef = new ProjectRef(m.group("slug"), versionName != null ? versionName : version);
        } else {
            projectRef = new ProjectRef(modpackProject, version);
        }

        final ModrinthModpackManifest newManifest =
            processModpack(projectRef, prevManifest);

        if (newManifest != null) {
            Manifests.cleanup(outputDirectory, prevManifest, newManifest, log);
            Manifests.save(outputDirectory, ModrinthModpackManifest.ID, newManifest);
        }

        return ExitCode.OK;
    }

    /**
     * @return the new manifest or null if already installed
     */
    private ModrinthModpackManifest processModpack(ProjectRef projectRef, ModrinthModpackManifest prevManifest) {

        try (ModrinthApiClient apiClient = new ModrinthApiClient(baseUrl, "install-modrinth-modpack", sharedFetchArgs.options())) {
            return apiClient.getProject(projectRef.getIdOrSlug())
                .onErrorMap(FailedRequestException::isNotFound,
                    throwable ->
                        new InvalidParameterException("Unable to locate requested project given " + projectRef, throwable)
                )
                .flatMap(project ->
                    apiClient.resolveProjectVersion(
                            project, projectRef, loader != null ? loader.asLoader() : null, gameVersion, defaultVersionType
                        )
                        .switchIfEmpty(Mono.defer(() -> Mono.error(new InvalidParameterException(
                            "Unable to find version given " + projectRef)))
                        )
                        .doOnNext(version -> log.debug("Resolved version={} from projectRef={}", version.getVersionNumber(), projectRef))
                        .publishOn(Schedulers.boundedElastic()) // since next item does I/O
                        .filter(version -> needsInstall(prevManifest, project, version))
                        .flatMap(version -> processVersion(apiClient, project, version))
                        .switchIfEmpty(Mono.defer(() -> {
                            try {
                                applyModLoader(prevManifest.getDependencies());
                            } catch (IOException e) {
                                return Mono.error(new GenericException("Failed to re-apply mod loader", e));
                            }
                            return Mono.just(prevManifest);
                        }))
                )
                .block();

        }
    }

    @NotNull
    private Mono<ModrinthModpackManifest> processVersion(ModrinthApiClient apiClient, Project project,
        Version version
    ) {
        final VersionFile versionFile = pickVersionFile(version);
        log.info("Installing version {} of {}", version.getVersionNumber(), project.getTitle());
        //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
        return apiClient.downloadMrPack(versionFile)
            .publishOn(Schedulers.boundedElastic())
            .flatMap(zipPath ->
                processModpackZip(apiClient, zipPath, project, version)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnTerminate(() -> {
                        try {
                            Files.delete(zipPath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
            );
    }

    @Blocking
    private boolean needsInstall(ModrinthModpackManifest prevManifest, Project project, Version version) {
        if (prevManifest != null) {
            if (prevManifest.getProjectSlug().equals(project.getSlug())
                && prevManifest.getVersionId().equals(version.getId())
                && prevManifest.getDependencies() != null
                && Manifests.allFilesPresent(outputDirectory, prevManifest)
            ) {
                if (forceSynchronize) {
                    log.info("Requested force synchronize of {}", project.getTitle());
                } else {
                    log.info("Modpack {} version {} is already installed",
                        project.getTitle(), version.getName()
                    );
                    return false;
                }
            }
        }
        return true;
    }

    @Blocking
    private Mono<ModrinthModpackManifest> processModpackZip(ModrinthApiClient apiClient, Path zipFile, Project project,
        Version version
    ) {
        final ModpackIndex modpackIndex;
        try {
            modpackIndex = IoStreams.readFileFromZip(zipFile, "modrinth.index.json", in ->
                ObjectMappers.defaultMapper().readValue(in, ModpackIndex.class)
            );
        } catch (IOException e) {
            return Mono.error(new GenericException("Failed to read modpack index", e));
        }

        if (modpackIndex == null) {
            return Mono.error(
                new InvalidParameterException("Modpack is missing modrinth.index.json")
            );
        }

        if (!Objects.equals("minecraft", modpackIndex.getGame())) {
            return Mono.error(
                new InvalidParameterException("Requested modpack is not for minecraft: " + modpackIndex.getGame()));
        }

        return processModpackFiles(apiClient, modpackIndex)
            .collectList()
            .map(modFiles ->
                Stream.of(
                        modFiles.stream(),
                        extractOverrides(zipFile, "overrides", "server-overrides")
                    )
                    .flatMap(Function.identity())
                    .collect(Collectors.toList())
            )
            .flatMap(paths -> {
                try {
                    applyModLoader(modpackIndex.getDependencies());
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to apply mod loader", e));
                }

                return Mono.just(ModrinthModpackManifest.builder()
                    .files(Manifests.relativizeAll(outputDirectory, paths))
                    .projectSlug(project.getSlug())
                    .versionId(version.getId())
                    .dependencies(modpackIndex.getDependencies())
                    .build());
            });
    }

    private void applyModLoader(Map<DependencyId, String> dependencies) throws IOException {
        log.debug("Applying mod loader from dependencies={}", dependencies);

        final String minecraftVersion = dependencies.get(DependencyId.minecraft);
        if (minecraftVersion == null) {
            throw new GenericException("Modpack dependencies missing minecraft version: " + dependencies);
        }

        final String forgeVersion = dependencies.get(DependencyId.forge);
        if (forgeVersion != null) {
            new ForgeInstaller().install(
                minecraftVersion,
                forgeVersion,
                outputDirectory,
                resultsFile,
                forceModloaderReinstall,
                null
            );
            return;
        }

        final String fabricVersion = dependencies.get(DependencyId.fabricLoader);
        if (fabricVersion != null) {
            new FabricLauncherInstaller(outputDirectory)
                .setResultsFile(resultsFile)
                .installUsingVersions(
                    minecraftVersion,
                    fabricVersion,
                    null
                );
            return;
        }

        final String quiltVersion = dependencies.get(DependencyId.quiltLoader);
        if (quiltVersion != null) {
            try (QuiltInstaller installer = new QuiltInstaller(QuiltInstaller.DEFAULT_REPO_URL,
                sharedFetchArgs.options(),
                outputDirectory,
                minecraftVersion
            )
                .setResultsFile(resultsFile)) {

                installer.installWithVersion(null, quiltVersion);
            }
        }

    }

    @SuppressWarnings("SameParameterValue")
    private Stream<Path> extractOverrides(Path zipFile, String... overridesDirs) {
        return Stream.of(overridesDirs)
            .flatMap(dir ->
            {
                final String prefix = dir + "/";
                final List<Path> extracted = new ArrayList<>();
                try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
                    ZipEntry entry;
                    while ((entry = zipIn.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            if (entry.getName().startsWith(prefix)) {
                                final Path outFile = outputDirectory.resolve(
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

    private Flux<Path> processModpackFiles(ModrinthApiClient apiClient, ModpackIndex modpackIndex) {
        return Flux.fromStream(modpackIndex.getFiles().stream()
                .filter(modpackFile ->
                    // env is optional
                    modpackFile.getEnv() == null
                        || modpackFile.getEnv().get(Env.server) != EnvType.unsupported
                )
            )
            .publishOn(Schedulers.boundedElastic())
            .flatMap(modpackFile ->
                {
                    final Path outFilePath = outputDirectory.resolve(modpackFile.getPath());
                    try {
                        //noinspection BlockingMethodInNonBlockingContext
                        Files.createDirectories(outFilePath.getParent());
                    } catch (IOException e) {
                        return Mono.error(new GenericException("Failed to created directory for file to download", e));
                    }

                    return apiClient.downloadFileFromUrl(
                        outFilePath,
                        modpackFile.getDownloads().get(0),
                        (uri, file, contentSizeBytes) -> log.info("Downloaded {}", modpackFile.getPath())
                    );
                }
            );
    }


}
