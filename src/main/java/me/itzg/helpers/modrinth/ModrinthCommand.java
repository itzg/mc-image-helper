package me.itzg.helpers.modrinth;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;
import static me.itzg.helpers.http.Fetch.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.Constants;
import me.itzg.helpers.modrinth.model.DependencyType;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.ProjectType;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionDependency;
import me.itzg.helpers.modrinth.model.VersionFile;
import me.itzg.helpers.modrinth.model.VersionType;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "modrinth", description = "Automates downloading of modrinth resources")
@Slf4j
public class ModrinthCommand implements Callable<Integer> {

    public static final String DATAPACKS_SUBDIR = "datapacks";
    @Option(names = "--projects", description = "Project ID or Slug",
        split = SPLIT_COMMA_NL, splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "id|slug"
    )
    List<String> projects;

    @Option(names = "--game-version", description = "Applicable Minecraft version", required = true)
    String gameVersion;

    @Option(names = "--loader", required = true, description = "Valid values: ${COMPLETION-CANDIDATES}")
    Loader loader;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--download-dependencies", defaultValue = "NONE",
        description = "Default is ${DEFAULT-VALUE}\nValid values: ${COMPLETION-CANDIDATES}")
    DownloadDependencies downloadDependencies;

    public enum DownloadDependencies {
        NONE,
        REQUIRED,
        /**
         * Implies {@link #REQUIRED}
         */
        OPTIONAL
    }

    @Option(names = "--allowed-version-type", defaultValue = "release", description = "Valid values: ${COMPLETION-CANDIDATES}")
    VersionType defaultVersionType;

    @Option(names = "--api-base-url", defaultValue = "${env:MODRINTH_API_BASE_URL:-https://api.modrinth.com}",
        description = "Default: ${DEFAULT-VALUE}"
    )
    String baseUrl;

    @Option(names = "--world-directory", defaultValue = "${env:LEVEL:-world}",
        description = "Used for datapacks, a path relative to the output directory or an absolute path\nDefault: ${DEFAULT-VALUE}"
    )
    Path worldDirectory;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    final Set<String/*projectId*/> projectsProcessed = Collections.synchronizedSet(new HashSet<>());

    @Override
    public Integer call() throws Exception {
        Files.createDirectories(outputDirectory);

        final ModrinthManifest prevManifest = loadManifest();

        final List<Path> outputFiles = processProjects(projects);

        final ModrinthManifest newManifest = ModrinthManifest.builder()
            .files(Manifests.relativizeAll(outputDirectory, outputFiles))
            .projects(projects)
            .build();

        Manifests.cleanup(outputDirectory, prevManifest, newManifest, log);

        Manifests.save(outputDirectory, ModrinthManifest.ID, newManifest);

        return ExitCode.OK;
    }

    private List<Path> processProjects(List<String> projects) {
        try (ModrinthApiClient modrinthApiClient = new ModrinthApiClient(baseUrl, "modrinth", sharedFetchArgs.options())) {
            //noinspection DataFlowIssue since it thinks block() may return null
            return
                modrinthApiClient.bulkGetProjects(
                    projects.stream()
                        .filter(s -> !s.trim().isEmpty())
                        .map(ProjectRef::parse)
                )
                .defaultIfEmpty(Collections.emptyList())
                .block()
                .stream()
                .flatMap(resolvedProject -> processProject(
                    modrinthApiClient, resolvedProject.getProjectRef(), resolvedProject.getProject()
                ))
                .collect(Collectors.toList());
        }
    }

    private ModrinthManifest loadManifest() throws IOException {
        final Path legacyManifestPath = outputDirectory.resolve(LegacyModrinthManifest.FILENAME);

        if (Files.exists(legacyManifestPath)) {
            final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

            final LegacyModrinthManifest legacyManifest = objectMapper.readValue(
                legacyManifestPath.toFile(),
                LegacyModrinthManifest.class
            );

            Files.delete(legacyManifestPath);

            return ModrinthManifest.builder()
                .timestamp(legacyManifest.getTimestamp())
                .files(new ArrayList<>(legacyManifest.getFiles()))
                .build();
        }

        return Manifests.load(outputDirectory, ModrinthManifest.ID, ModrinthManifest.class);
    }

    private Stream<Version> expandDependencies(ModrinthApiClient modrinthApiClient, Version version) {
        log.debug("Expanding dependencies of version={}", version);
        return version.getDependencies().stream()
            .filter(this::filterDependency)
            .filter(dep -> projectsProcessed.add(dep.getProjectId()))
            .flatMap(dep -> {
                projectsProcessed.add(dep.getProjectId());
                final Version depVersion;
                if (dep.getVersionId() == null) {
                    log.debug("Fetching versions of dep={} and picking", dep);
                    depVersion = pickVersion(
                        getVersionsForProject(modrinthApiClient, dep.getProjectId())
                    );
                }
                else {
                    log.debug("Fetching version for dep={}", dep);
                    depVersion = modrinthApiClient.getVersionFromId(dep.getVersionId())
                        .block();
                }
                if (depVersion != null) {
                    log.debug("Resolved version={} for dep={}", depVersion.getVersionNumber(), dep);
                    return Stream.concat(
                            Stream.of(depVersion),
                            expandDependencies(modrinthApiClient, depVersion)
                        )
                        .peek(expandedVer -> log.debug("Expanded dependency={} into version={}", dep, expandedVer));
                }
                else {
                    return Stream.empty();
                }
            });

    }

    private boolean filterDependency(VersionDependency dep) {
        if (downloadDependencies == null) {
            return false;
        }

        return (downloadDependencies == DownloadDependencies.REQUIRED && dep.getDependencyType() == DependencyType.required)
            || (
            downloadDependencies == DownloadDependencies.OPTIONAL
                && (dep.getDependencyType() == DependencyType.required || dep.getDependencyType() == DependencyType.optional)
        );
    }

    private Version pickVersion(List<Version> versions) {
        return this.pickVersion(versions, defaultVersionType);
    }

    private Version pickVersion(List<Version> versions, VersionType versionType) {
        for (final Version version : versions) {
            if (version.getVersionType().sufficientFor(versionType)) {
                return version;
            }
        }
        return null;
    }

    private Path download(boolean isDatapack, VersionFile versionFile) {
        final Path outPath;
        try {
            if (!isDatapack) {
                outPath = Files.createDirectories(outputDirectory
                        .resolve(loader.getType())
                    )
                    .resolve(versionFile.getFilename());
            }
            else {
                if (worldDirectory.isAbsolute()) {
                    outPath = Files.createDirectories(worldDirectory
                            .resolve(DATAPACKS_SUBDIR)
                        )
                        .resolve(versionFile.getFilename());
                }
                else {
                    outPath = Files.createDirectories(outputDirectory
                            .resolve(worldDirectory)
                            .resolve(DATAPACKS_SUBDIR)
                        )
                        .resolve(versionFile.getFilename());
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Creating mods directory", e);
        }

        try {
            return fetch(URI.create(versionFile.getUrl()))
                .userAgentCommand("modrinth")
                .toFile(outPath)
                .skipUpToDate(true)
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Downloading mod file", e);
        }
    }

    private List<Version> getVersionsForProject(ModrinthApiClient modrinthApiClient, String project) {
        final List<Version> versions = modrinthApiClient.getVersionsForProject(
                project, loader, gameVersion
            )
            .block();
        if (versions == null) {
            throw new GenericException("Unable to retrieve versions for project " + project);
        }
        return versions;
    }


    private Stream<Path> processProject(ModrinthApiClient modrinthApiClient, ProjectRef projectRef, Project project) {
        if (project.getProjectType() != ProjectType.mod) {
            throw new InvalidParameterException(
                String.format("Requested project '%s' is not a mod, but has type %s",
                    project.getTitle(), project.getProjectType()
                ));
        }

        log.debug("Starting with project='{}' slug={}", project.getTitle(), project.getSlug());

        if (projectsProcessed.add(project.getId())) {
            final Version version;
            try {
                version = modrinthApiClient.resolveProjectVersion(
                        project, projectRef, loader, gameVersion, defaultVersionType
                    )
                    .block();
            } catch (NoApplicableVersionsException | NoFilesAvailableException e) {
                throw new InvalidParameterException(e.getMessage(), e);
            }

            if (version != null) {
                if (version.getFiles().isEmpty()) {
                    throw new GenericException(String.format("Project %s has no files declared", project.getSlug()));
                }

                final boolean isDatapack = isDatapack(version);

                return Stream.concat(
                        Stream.of(version),
                        expandDependencies(modrinthApiClient, version)
                    )
                    .map(ModrinthApiClient::pickVersionFile)
                    .map(versionFile -> download(isDatapack, versionFile))
                    .flatMap(downloadedFile -> !isDatapack ? expandIfZip(downloadedFile) : Stream.empty());
            }
            else {
                throw new InvalidParameterException(
                    String.format("Project %s does not have any matching versions for loader %s, game version %s",
                        projectRef, loader, gameVersion
                    ));
            }
        }
        return Stream.empty();
    }

    private boolean isDatapack(Version version) {
        return
            version.getLoaders() != null
            && version.getLoaders().size() == 1
            && version.getLoaders().get(0).equals(Constants.LOADER_DATAPACK);
    }

    /**
     * If downloadedFile ends in .zip, then expand it, return its files and given file.
     *
     * @return a stream of at least the given file along with unzipped contents
     */
    private Stream<Path> expandIfZip(Path downloadedFile) {
        if (downloadedFile.getFileName().toString().endsWith(".zip")) {
            return Stream.concat(
                Stream.of(downloadedFile),
                expandZip(downloadedFile)
            );
        }
        else {
            return Stream.of(downloadedFile);
        }
    }

    private Stream<Path> expandZip(Path zipFile) {
        log.debug("Unzipping downloaded file={}", zipFile);
        final Path outDir = zipFile.getParent();

        final ArrayList<Path> contents = new ArrayList<>();

        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    final String name = entry.getName();
                    final Path resolved = outDir.resolve(name);
                    if (!Files.exists(resolved)) {
                        log.debug("Expanding from zip to={}", resolved);
                        if (name.contains("/")) {
                            Files.createDirectories(resolved.getParent());
                        }
                        Files.copy(zipIn, resolved);
                    }
                    else {
                        log.debug("File={} from zip already exists", resolved);
                    }
                    contents.add(resolved);
                }
            }
        } catch (IOException e) {
            throw new GenericException("Unable to unzip downloaded file", e);
        }

        return contents.stream();
    }
}
