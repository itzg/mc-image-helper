package me.itzg.helpers.modrinth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.http.Uris.QueryParameters;
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

import static me.itzg.helpers.McImageHelper.OPTION_SPLIT_COMMAS;
import static me.itzg.helpers.http.Fetch.fetch;

@Command(name = "modrinth", description = "Automates downloading of modrinth resources")
@Slf4j
public class ModrinthCommand implements Callable<Integer> {

    private final String baseUrl;

    @Option(names = "--projects", description = "Project ID or Slug", split = OPTION_SPLIT_COMMAS,
        paramLabel = "id|slug"
    )
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
    VersionType defaultVersionType;

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
        return projects.stream()
            .flatMap(this::processProject)
            .collect(Collectors.toList());
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

    private Stream<Version> expandDependencies(Version version) {
        log.debug("Expanding dependencies of version={}", version);
        return version.getDependencies().stream()
            .filter(dep ->
                (dep.getDependencyType() == DependencyType.required ||
                    downloadOptionalDependencies && dep.getDependencyType() == DependencyType.optional)
            )
            .filter(dep -> projectsProcessed.add(dep.getProjectId()))
            .flatMap(dep -> {
                projectsProcessed.add(dep.getProjectId());
                try {
                    final Version depVersion;
                    if (dep.getVersionId() == null) {
                        log.debug("Fetching versions of dep={} and picking", dep);
                        depVersion = pickVersion(
                            getVersionsForProject(dep.getProjectId())
                        );
                    }
                    else {
                        log.debug("Fetching version for dep={}", dep);
                        depVersion = getVersion(dep.getVersionId());
                    }
                    if (depVersion != null) {
                        log.debug("Resolved version={} for dep={}", depVersion, dep);
                        return Stream.concat(
                                Stream.of(depVersion),
                                expandDependencies(depVersion)
                            )
                            .peek(expandedVer -> log.debug("Expanded dependency={} into version={}", dep, expandedVer));
                    }
                    else {
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
            .userAgentCommand("modrinth")
            .toObject(Version.class)
            .execute();
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

    private Path download(ProjectType projectType, VersionFile versionFile) {
        if (log.isDebugEnabled()) {
            log.debug("Downloading {}", versionFile);
        }
        else {
            log.info("Downloading {}", versionFile.getFilename());
        }

        if (projectType != ProjectType.mod) {
            throw new InvalidParameterException("Only mod project types can be downloaded for now");
        }
        final Path outPath;
        try {
            outPath = Files.createDirectories(outputDirectory.resolve(loader.getType()))
                .resolve(versionFile.getFilename());
        } catch (IOException e) {
            throw new RuntimeException("Creating mods directory", e);
        }

        try {
            return fetch(URI.create(versionFile.getUrl()))
                .userAgentCommand("modrinth")
                .toFile(outPath)
                .skipExisting(true)
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Downloading mod file", e);
        }
    }

    private Project getProject(String projectIdOrSlug) {
        return fetch(Uris.populateToUri(
            baseUrl + "/project/{id|slug}",
            projectIdOrSlug
        ))
            .userAgentCommand("modrinth")
            .toObject(Project.class)
            .assemble()
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable ->
                    new InvalidParameterException("Unable to locate requested project given " + projectIdOrSlug, throwable)
            )
            .block();
    }

    private List<Version> getVersionsForProject(String project) {
        try {
            return fetch(Uris.populateToUri(
                baseUrl + "/project/{id|slug}/version",
                QueryParameters.queryParameters()
                    .addStringArray("loaders", loader != null ? loader.toString() : null)
                    .addStringArray("game_versions", gameVersion),
                project
            ))
                .userAgentCommand("modrinth")
                .toObjectList(Version.class)
                .execute();
        } catch (IOException e) {
            throw new RuntimeException("Getting versions for project " + project, e);
        }
    }

    private Version getVersionFromId(String versionId) {
        return fetch(Uris.populateToUri(
            baseUrl + "/version/{id}",
            versionId
        ))
            .userAgentCommand("modrinth")
            .toObject(Version.class)
            .execute();
    }


    private Stream<? extends Path> processProject(String projectRefStr) {
        log.debug("Starting with projectRef={}", projectRefStr);

        final ProjectRef projectRef = ProjectRef.parse(projectRefStr);
        final Project project = getProject(projectRef.getIdOrSlug());

        if (projectsProcessed.add(project.getId())) {
            final Version version = resolveProjectVersion(project, projectRef);

            if (version != null) {
                if (version.getFiles().isEmpty()) {
                    throw new GenericException(String.format("Project %s has no files declared", project.getSlug()));
                }

                return Stream.concat(
                        Stream.of(version),
                        expandDependencies(version)
                    )
                    .map(ModrinthApiClient::pickVersionFile)
                    .map(versionFile -> download(project.getProjectType(), versionFile))
                    .flatMap(this::expandIfZip);
            }
        }
        return Stream.empty();
    }

    private Version resolveProjectVersion(Project project, ProjectRef projectRef) {
        if (projectRef.hasVersionType()) {
            return pickVersion(getVersionsForProject(project.getId()), projectRef.getVersionType());
        }
        else if (projectRef.hasVersionId()) {
            return getVersionFromId(projectRef.getVersionId());
        }
        else {
            final List<Version> versions = getVersionsForProject(project.getId());
            return pickVersion(versions);
        }
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
