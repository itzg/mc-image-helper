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
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionFile;
import me.itzg.helpers.modrinth.model.VersionType;
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
    description = "Supports installation of Modrinth modpacks along with the associated mod loader"
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

    @Option(names = "--version-id", description = "Version ID (not name) from the file's metadata")
    String versionId;

    @Option(names = "--game-version", description = "Applicable Minecraft version")
    String gameVersion;

    @Option(names = "--loader", description = "Valid values: ${COMPLETION-CANDIDATES}")
    Loader loader;

    @Option(names = "--default-version-type", defaultValue = "release",
        description = "Valid values: ${COMPLETION-CANDIDATES}"
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

    @Option(names = "--api-base-url", defaultValue = "${env:MODRINTH_API_BASE_URL:-https://api.modrinth.com/v2}")
    String baseUrl;

    @CommandLine.ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        final ModrinthModpackManifest prevManifest = Manifests.load(
            outputDirectory, ModrinthModpackManifest.ID, ModrinthModpackManifest.class);

        final ProjectRef projectRef;
        final Matcher m = MODPACK_PAGE_URL.matcher(modpackProject);
        if (m.matches()) {
            final String versionName = m.group("versionName");
            if (versionName != null && versionId != null) {
                throw new InvalidParameterException("Cannot provide both project file URL and version ID");
            }
            projectRef = new ProjectRef(m.group("slug"), versionId, versionName);
        } else {
            projectRef = new ProjectRef(modpackProject, versionId, null);
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

        try (ModrinthApiClient apiClient = new ModrinthApiClient(baseUrl, sharedFetchArgs.options())) {
            return apiClient.getProject(projectRef.getIdOrSlug())
                .onErrorMap(FailedRequestException::isNotFound,
                    throwable ->
                        new InvalidParameterException("Unable to locate requested project given " + projectRef, throwable)
                )
                .flatMap(project ->
                    apiClient.resolveProjectVersion(
                            project, projectRef, loader, gameVersion, defaultVersionType
                        )
                        .switchIfEmpty(Mono.defer(() -> Mono.error(new InvalidParameterException(
                            "Unable to find version given " + projectRef))))
                        .doOnNext(version -> log.debug("Resolved version={} from projectRef={}", version, projectRef))
                        .publishOn(Schedulers.boundedElastic()) // since next item does I/O
                        .filter(version -> needsInstall(prevManifest, project, version))
                        .flatMap(version -> {
                            final VersionFile versionFile = pickVersionFile(version);
                            log.debug("Picked versionFile={} from version={}", versionFile, version);
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
                        })
                )
                .block();

        }
    }

    private boolean needsInstall(ModrinthModpackManifest prevManifest, Project project, Version version) {
        if (prevManifest != null) {
            if (prevManifest.getProjectSlug().equals(project.getSlug())
                && prevManifest.getVersionId().equals(version.getId())
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

        // Fail-fast for Quilt non-support
        if (modpackIndex.getDependencies().get(DependencyId.quiltLoader) != null) {
            throw new GenericException(
                "Quilt mod loader is not yet supported. Please choose alternate file that uses Fabric, if possible");
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
            .handle((paths, sink) -> {
                final String minecraftVersion;
                try {
                    minecraftVersion = applyModloader(modpackIndex.getDependencies());
                } catch (IOException e) {
                    sink.error(new GenericException("Failed to apply mod loader", e));
                    return;
                }

                if (resultsFile != null) {
                    try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                        resultsFileWriter.write("VERSION", minecraftVersion);
                    } catch (IOException e) {
                        sink.error(new GenericException("Failed to write results file", e));
                        return;
                    }
                }

                sink.next(ModrinthModpackManifest.builder()
                    .files(Manifests.relativizeAll(outputDirectory, paths))
                    .projectSlug(project.getSlug())
                    .versionId(version.getId())
                    .build());
            });
    }

    private String applyModloader(Map<DependencyId, String> dependencies) throws IOException {
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
            return minecraftVersion;
        }

        final String fabricVersion = dependencies.get(DependencyId.fabricLoader);
        if (fabricVersion != null) {
            new FabricLauncherInstaller(outputDirectory, resultsFile)
                .installUsingVersions(
                    minecraftVersion,
                    fabricVersion,
                    null
                );
            return minecraftVersion;
        }

        if (dependencies.get(DependencyId.quiltLoader) != null) {
            throw new GenericException("Quilt modloader is not yet supported");
        }

        return minecraftVersion;
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
                    modpackFile.getEnv().get(Env.server) == EnvType.required
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
