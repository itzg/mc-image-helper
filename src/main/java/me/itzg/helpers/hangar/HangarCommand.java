package me.itzg.helpers.hangar;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;
import static me.itzg.helpers.singles.NormalizeOptions.normalizeOptionList;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.ChecksumAlgo;
import me.itzg.helpers.files.Checksums;
import me.itzg.helpers.files.FileHashInvalidException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.hangar.model.FileInfo;
import me.itzg.helpers.hangar.model.Platform;
import me.itzg.helpers.hangar.model.PlatformVersionDownload;
import me.itzg.helpers.hangar.model.Version;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;

@Command(name = "hangar", description = "Automates downloading of plugins from Hangar")
@Slf4j
public class HangarCommand implements Callable<Integer> {

    public static final String PLUGINS_SUBDIR = "plugins";
    private static final int MAX_DOWNLOAD_ATTEMPTS = 2;

    @Option(
        names = "--projects",
        description = "Project slug or ID. Can be <slug|ID>, <slug|ID>:<version name>,"
            + " '@'<filename with ref per line (supports # comments)>"
            + "%nAppend '?' to mark a project as optional (skipped with a warning if unavailable)."
            + "%nExamples: ViaVersion, ViaVersion:5.12.2, Maintenance?, @/path/to/hangar-plugins.txt"
            + "%nEmbedded comments are allowed.",
        split = SPLIT_COMMA_NL,
        splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "slug|id[?][:version]"
    )
    public void setProjects(List<String> projects) {
        this.projects = normalizeOptionList(projects);
    }
    private List<String> projects = Collections.emptyList();

    @Option(names = "--game-version", description = "Applicable Minecraft version", required = true)
    String gameVersion;

    @Option(names = "--platform", defaultValue = "PAPER",
        description = "Default is ${DEFAULT-VALUE}%nValid values: ${COMPLETION-CANDIDATES}")
    Platform platform;

    @Option(names = "--channel", defaultValue = "Release",
        description = "The channel to select the latest version from when a project does not declare a version"
            + "%nDefault is ${DEFAULT-VALUE}")
    String channel;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR",
        description = "Plugins are downloaded into the plugins subdirectory of this directory")
    Path outputDirectory;

    @Option(names = "--skip-existing", defaultValue = "${env:HANGAR_SKIP_EXISTING}")
    boolean skipExisting = true;

    @Option(names = "--skip-up-to-date", defaultValue = "${env:HANGAR_SKIP_UP_TO_DATE}")
    boolean skipUpToDate = true;

    @Option(names = "--api-base-url", defaultValue = "${env:HANGAR_API_BASE_URL:-https://hangar.papermc.io}",
        description = "Default: ${DEFAULT-VALUE}"
    )
    String baseUrl;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        Files.createDirectories(outputDirectory);

        final List<Path> outputFiles = processProjects(projects);

        final HangarManifest newManifest = HangarManifest.builder()
            .files(Manifests.relativizeAll(outputDirectory, outputFiles))
            .projects(projects)
            .build();

        final HangarManifest prevManifest = Manifests.load(outputDirectory, HangarManifest.ID, HangarManifest.class);
        Manifests.cleanup(outputDirectory, prevManifest, newManifest, log);
        Manifests.apply(outputDirectory, HangarManifest.ID, newManifest);

        return ExitCode.OK;
    }

    private List<Path> processProjects(List<String> projects) {
        if (projects.isEmpty()) {
            return Collections.emptyList();
        }

        try (SharedFetch sharedFetch = Fetch.sharedFetch("hangar", sharedFetchArgs.options())) {
            final HangarApiClient hangarApiClient = new HangarApiClient(baseUrl, sharedFetch);

            return expandProjectListings(projects).stream()
                .map(ProjectRef::parse)
                .flatMap(projectRef -> processProject(hangarApiClient, sharedFetch, projectRef))
                .collect(Collectors.toList());
        }
    }

    private List<String> expandProjectListings(List<String> projects) {
        return projects.stream()
            .distinct()
            // handle @-file containing refs
            .flatMap(ref -> {
                if (ref.startsWith("@")) {
                    try {
                        return Files.readAllLines(Paths.get(ref.substring(1))).stream()
                            .map(s -> s
                                .replaceFirst("#.*", "")
                                .trim()
                            )
                            .filter(s -> !s.isEmpty());
                    } catch (IOException e) {
                        throw new GenericException("Reading project refs from file: " + ref.substring(1), e);
                    }
                }
                else {
                    return Stream.of(ref);
                }
            })
            .collect(Collectors.toList());
    }

    private Stream<Path> processProject(HangarApiClient hangarApiClient, SharedFetch sharedFetch, ProjectRef projectRef) {
        final PlatformVersionDownload download;
        try {
            download = resolveDownload(hangarApiClient, projectRef)
                .block();
        } catch (InvalidParameterException e) {
            if (projectRef.isOptional()) {
                log.warn("Optional project '{}' is not available — skipping: {}", projectRef.getSlugOrId(), e.getMessage());
                return Stream.empty();
            }
            throw e;
        }

        return Stream.of(download(sharedFetch, download));
    }

    private Mono<PlatformVersionDownload> resolveDownload(HangarApiClient hangarApiClient, ProjectRef projectRef) {
        final Mono<Version> version = projectRef.getVersionName() != null ?
            hangarApiClient.getVersion(projectRef.getSlugOrId(), projectRef.getVersionName())
            : hangarApiClient.getLatestVersion(projectRef.getSlugOrId(), platform, gameVersion, channel);

        return version
            .switchIfEmpty(Mono.error(() -> InvalidParameterException.formatted(
                "Project %s does not have any versions in channel %s for platform %s, game version %s",
                projectRef.getSlugOrId(), channel, platform, gameVersion
            )))
            .doOnNext(resolved -> log.debug("Resolved version {} of project {}", resolved.getName(), projectRef))
            .flatMap(resolved ->
                Mono.justOrEmpty(resolved.getDownloads() != null ? resolved.getDownloads().get(platform.name()) : null)
                    .switchIfEmpty(Mono.error(() -> InvalidParameterException.formatted(
                        "Version %s of project %s does not have a download for platform %s",
                        resolved.getName(), projectRef.getSlugOrId(), platform
                    )))
            );
    }

    private Path download(SharedFetch sharedFetch, PlatformVersionDownload download) {
        final Path pluginsDir;
        try {
            pluginsDir = Files.createDirectories(outputDirectory.resolve(PLUGINS_SUBDIR));
        } catch (IOException e) {
            throw new GenericException("Creating plugins directory", e);
        }

        try {
            if (download.getDownloadUrl() != null && download.getFileInfo() != null) {
                return downloadHostedFile(sharedFetch, download, pluginsDir);
            }
            else if (download.getExternalUrl() != null) {
                // External downloads declare neither a filename nor a hash, so let the response provide the filename
                return sharedFetch.fetch(URI.create(download.getExternalUrl()))
                    .userAgentCommand("hangar")
                    .toDirectory(pluginsDir)
                    .skipExisting(skipExisting)
                    .skipUpToDate(skipUpToDate)
                    .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                    .execute();
            }
            else {
                throw new GenericException("Download is missing a URL: " + download);
            }
        } catch (IOException e) {
            throw new GenericException("Downloading file", e);
        }
    }

    private Path downloadHostedFile(SharedFetch sharedFetch, PlatformVersionDownload download, Path pluginsDir)
        throws IOException {
        final FileInfo fileInfo = download.getFileInfo();
        final Path outPath = pluginsDir.resolve(fileInfo.getName());

        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            final Path file = sharedFetch.fetch(URI.create(download.getDownloadUrl()))
                .userAgentCommand("hangar")
                .toFile(outPath)
                .skipExisting(skipExisting)
                .skipUpToDate(skipUpToDate)
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .execute();

            // Versions of a project can reuse a filename, so the hash also detects an outdated existing file
            if (fileInfo.getSha256Hash() == null || Checksums.valid(file, ChecksumAlgo.SHA256, fileInfo.getSha256Hash())) {
                return file;
            }

            Files.delete(file);
            if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
                log.warn("Checksum of {} does not match, downloading again", file);
            }
        }

        throw new FileHashInvalidException(
            String.format("Incorrect checksum of %s downloaded from %s", fileInfo.getName(), download.getDownloadUrl())
        );
    }
}
