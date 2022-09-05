package me.itzg.helpers.modrinth;

import static me.itzg.helpers.http.Fetch.fetch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.Manifests;
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

@Command(name = "modrinth", description = "Automates downloading of modrinth resources")
@Slf4j
public class ModrinthCommand implements Callable<Integer> {

    public static final TypeReference<List<Version>> VERSION_LIST = new TypeReference<List<Version>>() {
    };
    public static final String MODS_SUBDIR = "mods";

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

        final Path manifestPath = outputDirectory.resolve(Manifest.FILENAME);

        final Manifest oldManifest;
        if (Files.exists(manifestPath)) {
            oldManifest = objectMapper.readValue(manifestPath.toFile(), Manifest.class);
            log.debug("Loaded existing manifest={}", oldManifest);
        } else {
            oldManifest = null;
        }

        final List<Path> outputFiles = projects.stream()
            .flatMap(this::processProject)
            .collect(Collectors.toList());

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

    private Stream<Version> expandDependencies(Version version) {
        log.debug("Expanding dependencies of version={}", version);
        return version.getDependencies().stream()
            .filter(dep ->
                projectsProcessed.add(dep.getProjectId()) &&
                    (dep.getDependencyType() == DependencyType.required ||
                        downloadOptionalDependencies && dep.getDependencyType() == DependencyType.optional)
            )
            .flatMap(dep -> {
                try {
                    final Version depVersion;
                    if (dep.getVersionId() == null) {
                        log.debug("Fetching versions of dep={} and picking", dep);
                        depVersion = pickVersion(
                            getVersionsForProject(dep.getProjectId())
                        );
                    } else {
                        log.debug("Fetching version for dep={}", dep);
                        depVersion = getVersion(dep.getVersionId());
                    }
                    if (depVersion != null) {
                        log.debug("Resolved version={} for dep={}", depVersion, dep);
                        return Stream.concat(
                                Stream.of(depVersion),
                                expandDependencies(depVersion)
                            )
                            .peek(expandedVer -> {
                                log.debug("Expanded dependency={} into version={}", dep, expandedVer);
                            });
                    } else {
                        return Stream.empty();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

    }

    private Version getVersion(String versionId) throws IOException {
        return fetch(Uris.populateToUri(
            baseUrl + "/version/{id}", versionId
        ))
            .toObject(Version.class)
            .execute();
    }

    private Version pickVersion(List<Version> versions) {
        for (final Version version : versions) {
            if (version.getVersionType().sufficientFor(versionType)) {
                return version;
            }
        }
        return null;
    }

    private Path download(ProjectType projectType, VersionFile versionFile) {
        if (log.isDebugEnabled()) {
            log.debug("Downloading {}", versionFile);
        } else {
            log.info("Downloading {}", versionFile.getFilename());
        }

        if (projectType != ProjectType.mod) {
            throw new IllegalStateException("Only mod project types can be downloaded for now");
        }
        final Path outPath;
        try {
            outPath = Files.createDirectories(outputDirectory.resolve(MODS_SUBDIR))
                .resolve(versionFile.getFilename());
        } catch (IOException e) {
            throw new RuntimeException("Creating mods directory", e);
        }

        try {
            return fetch(URI.create(versionFile.getUrl()))
                .toFile(outPath)
                .skipExisting(true)
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Downloading mod file", e);
        }
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

    private Project getProject(String projectIdOrSlug) {
        try {
            return fetch(Uris.populateToUri(
                baseUrl + "/project/{id|slug}",
                projectIdOrSlug
            ))
                .toObject(Project.class)
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Getting project " + projectIdOrSlug, e);
        }
    }

    private List<Version> getVersionsForProject(String project) {
        try {
            return fetch(Uris.populateToUri(
                baseUrl + "/project/{id|slug}/version?loaders={loader}&game_versions={gameVersion}",
                project, arrayOfQuoted(loader.toString()), arrayOfQuoted(gameVersion)
            ))
                .toObjectList(Version.class)
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Getting versions for project " + project, e);
        }
    }


    private String arrayOfQuoted(String value) {
        return "[\"" + value + "\"]";
    }

    private Stream<? extends Path> processProject(String projectRef) {
        log.debug("Starting with projectRef={}", projectRef);

        final Project project = getProject(projectRef);
        if (projectsProcessed.add(project.getId())) {
            final List<Version> versions = getVersionsForProject(project.getId());
            final Version version = pickVersion(versions);

            if (version != null) {
                return Stream.concat(
                        Stream.of(version),
                        expandDependencies(version)
                    )
                    .map(this::pickVersionFile)
                    .map(versionFile -> download(project.getProjectType(), versionFile));
            }
        }
        return Stream.empty();
    }
}
