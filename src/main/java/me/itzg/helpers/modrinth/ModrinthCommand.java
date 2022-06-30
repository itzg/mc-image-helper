package me.itzg.helpers.modrinth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.Manifests;
import me.itzg.helpers.http.HttpClientException;
import me.itzg.helpers.http.ReactorNettyBits;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.DependencyType;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.ProjectType;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionFile;
import me.itzg.helpers.modrinth.model.VersionType;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "modrinth", description = "Automates downloading of modrinth resources")
@Slf4j
public class ModrinthCommand implements Callable<Integer> {

    public static final TypeReference<List<Version>> VERSION_LIST = new TypeReference<List<Version>>() {
    };
    public static final String MANIFEST_FILENAME = ".modrinth-files.manifest";
    public static final String MODS_SUBDIR = "mods";

    private final ReactorNettyBits bits = new ReactorNettyBits();

    private final String baseUrl;

    @Option(names = "--projects", description = "Project ID or Slug", required = true, split = ",", paramLabel = "id|slug")
    List<String> projects;

    @Option(names = "--game-version", description = "Applicable Minecraft version", required = true)
    String gameVersion;

    @Option(names = "--loader", required = true, description = "Valid values: ${COMPLETION-CANDIDATES}")
    Loader loader;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--download-optional-dependencies")
    boolean downloadOptionalDependencies;

    @Option(names = "--allowed-version-type", defaultValue = "release", description = "Valid values: ${COMPLETION-CANDIDATES}")
    VersionType versionType;

    final Set<Path> outputFiles = Collections.synchronizedSet(new HashSet<>());
    final Set<String/*projectId*/> projectsProcessed = Collections.synchronizedSet(new HashSet<>());

    @SuppressWarnings("unused")
    public ModrinthCommand() {
        this("https://api.modrinth.com/v2");
    }

    public ModrinthCommand(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Integer call() throws Exception {
        final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

        Files.createDirectories(outputDirectory);

        final Path manifestPath = outputDirectory.resolve(MANIFEST_FILENAME);

        final Manifest oldManifest;
        if (Files.exists(manifestPath)) {
            oldManifest = objectMapper.readValue(manifestPath.toFile(), Manifest.class);
            log.debug("Loaded existing manifest={}", oldManifest);
        } else {
            oldManifest = null;
        }

        Flux.fromIterable(projects)
            .parallel()
            .runOn(Schedulers.parallel())
            .doOnNext(projectRef -> log.debug("Starting with projectRef={}", projectRef))
            .flatMap(this::getProject)
            .doOnNext(project -> projectsProcessed.add(project.getId()))
            .flatMap(project ->
                getVersionsForProject(project.getId())
                    .mapNotNull(this::pickVersion)
                    .doOnNext(version -> log.debug("Picked version={} for project={}", version, project))
                    .expand(this::expandDependencies)
                    .mapNotNull(this::pickVersionFile)
                    .doOnNext(versionFile -> log.debug("VersionFile={}", versionFile))
                    .flatMap(versionFile -> download(project.getProjectType(), versionFile))
                    .doOnNext(outputFiles::add)
                    .doOnNext(path -> log.debug("Wrote file={} for project={}", path, project))
            )
            .then()
            .block();

        final Manifest newManifest = Manifest.builder()
            .timestamp(Instant.now())
            .files(
                outputFiles.stream()
                    .map(path -> outputDirectory.relativize(path))
                    .map(Path::toString)
                    .collect(Collectors.toSet())
            )
            .build();

        if (oldManifest != null) {
            Manifests.cleanup(outputDirectory, oldManifest.getFiles(), newManifest.getFiles(),
                file -> log.debug("Deleting old file={}", file)
            );
        }

        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(manifestPath.toFile(), newManifest);

        return ExitCode.OK;
    }

    private Flux<Version> expandDependencies(Version version) {
        return Flux.fromStream(version.getDependencies().stream()
                .filter(versionDependency ->
                    projectsProcessed.add(versionDependency.getProjectId()) &&
                        (versionDependency.getDependencyType() == DependencyType.required ||
                            downloadOptionalDependencies && versionDependency.getDependencyType() == DependencyType.optional)
                )
            )
            .flatMap(versionDependency -> {
                if (versionDependency.getVersionId() == null) {
                    return getVersionsForProject(versionDependency.getProjectId())
                        .mapNotNull(this::pickVersion);
                } else {
                    return getVersion(versionDependency.getVersionId());
                }
            })
            .doOnNext(depVersion -> log.debug("Expanded depVersion={} from version={}", depVersion, version));
    }

    private Mono<Version> getVersion(String versionId) {
        return bits.jsonClient()
            .get()
            .uri(Uris.populate(
                baseUrl + "/versions/{id}", versionId
            ))
            .responseSingle(bits.readInto(Version.class));
    }

    private Version pickVersion(List<Version> versions) {
        for (final Version version : versions) {
            if (version.getVersionType().sufficientFor(versionType)) {
                return version;
            }
        }
        return null;
    }

    private Mono<Path> download(ProjectType projectType, VersionFile versionFile) {
        if (log.isDebugEnabled()) {
            log.debug("Downloading {}", versionFile);
        } else {
            log.info("Downloading {}", versionFile.getFilename());
        }

        if (projectType != ProjectType.mod) {
            throw new IllegalStateException("Only mod project types can be downloaded for now");
        }
        final Path outPath = outputDirectory
            .resolve(MODS_SUBDIR)
            .resolve(versionFile.getFilename());

        if (Files.exists(outPath)) {
            log.debug("Output file={} already exists", outPath);
            return Mono.just(outPath);
        }

        return bits.client()
            .followRedirect(true)
            .get()
            .uri(versionFile.getUrl())
            .responseContent()
            .aggregate()
            .asInputStream()
            .publishOn(Schedulers.boundedElastic())
            .map(inputStream -> {
                try {
                    try {
                        Files.createDirectories(outPath.getParent());
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to create directory for downloaded file", e);
                    }

                    Files.copy(inputStream, outPath);

                    return outPath;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.warn("Unable to close download aggregate stream", e);
                    }
                }
            })
            .doOnNext(outputFiles::add);
    }

    private VersionFile pickVersionFile(Version version) {
        if (version.getFiles().size() == 1) {
            return version.getFiles().get(0);
        } else {
            return version.getFiles().stream()
                .filter(VersionFile::isPrimary)
                .findFirst()
                .orElse(null);
        }
    }

    private Mono<Project> getProject(String projectIdOrSlug) throws HttpClientException {
        return bits.jsonClient()
            .get()
            .uri(Uris.populate(
                baseUrl + "/project/{id|slug}",
                projectIdOrSlug
            ))
            .responseSingle(bits.readInto(Project.class));
    }

    private Mono<List<Version>> getVersionsForProject(String project) {
        return bits.jsonClient()
            .get()
            .uri(Uris.populate(
                baseUrl + "/project/{id|slug}/version?loaders={loader}&game_versions={gameVersion}",
                project, arrayOfQuoted(loader.toString()), arrayOfQuoted(gameVersion)
            ))
            .responseSingle(bits.readInto(VERSION_LIST));
    }


    private String arrayOfQuoted(String value) {
        return "[\"" + value + "\"]";
    }
}
