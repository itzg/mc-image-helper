package me.itzg.helpers.paper;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.ManifestException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.paper.PaperDownloadsClient.VersionBuildFile;
import me.itzg.helpers.paper.model.VersionMeta;
import me.itzg.helpers.sync.MultiCopyManifest;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "install-paper", description = "Installs selected PaperMC")
@Slf4j
public class InstallPaperCommand implements Callable<Integer> {

    @Option(names = "--check-updates", description = "Check for updates and exit with status code 0 when available")
    boolean requestCheckUpdates;

    @ArgGroup
    Inputs inputs = new Inputs();

    static class Inputs {

        @Option(names = "--url", description = "Use a custom URL location")
        URI downloadUrl;

        @ArgGroup(exclusive = false)
        Coordinates coordinates = new Coordinates();

        static class Coordinates {

            @Spec
            CommandLine.Model.CommandSpec spec;

            @Option(names = "--project", defaultValue = "paper")
            String project;

            private static final Pattern ALLOWED_VERSIONS = Pattern.compile("latest|\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT|-pre\\d+)?",
                Pattern.CASE_INSENSITIVE
            );

            @Option(names = "--version", defaultValue = "latest", description = "May be 'latest' or specific version")
            public void setVersion(String version) {
                final Matcher m = ALLOWED_VERSIONS.matcher(version);
                if (!m.matches()) {
                    throw new ParameterException(spec.commandLine(), "Invalid value for minecraft version: " + version);
                }
                this.version = version;
            }

            String version;

            @Option(names = "--build")
            Integer build;

            @Option(names = "--channel", description = "This is ignored for now",
                defaultValue = "default")
            RequestedChannel channel;
        }
    }

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".")
    Path outputDirectory;

    @Option(names = "--base-url", defaultValue = "https://fill.papermc.io")
    String baseUrl;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @ArgGroup
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Builder
    private static class Result {

        final PaperManifest newManifest;
        final Path serverJar;
        final String version;
    }

    @Override
    public Integer call() throws Exception {
        final PaperManifest oldManifest = loadOldManifest();

        final Result result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(baseUrl, sharedFetchArgs.options())) {
            if (inputs.downloadUrl != null) {
                if (requestCheckUpdates) {
                    throw new InvalidParameterException("Cannot check for updates with custom input");
                }
                result = downloadCustom(inputs.downloadUrl);
            }
            else {
                if (requestCheckUpdates) {
                    return checkForUpdates(client, oldManifest,
                        inputs.coordinates.project, inputs.coordinates.version, inputs.coordinates.build
                    );
                }

                result = downloadUsingCoordinates(client, inputs.coordinates.project,
                    inputs.coordinates.version, inputs.coordinates.build
                )
                    .block();
            }
        }

        if (result == null) {
            throw new GenericException("Result from download was absent");
        }

        if (resultsFile != null) {
            try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
                results.writeServer(result.serverJar);
                results.writeVersion(result.version);
                results.writeType(inputs.coordinates.project != null ?
                    inputs.coordinates.project.toUpperCase() : "PAPER");
            }
        }

        Manifests.cleanup(outputDirectory, oldManifest, result.newManifest, log);
        Manifests.save(outputDirectory, PaperManifest.ID, result.newManifest);

        return ExitCode.OK;
    }

    private Integer checkForUpdates(PaperDownloadsClient client, PaperManifest oldManifest,
        String project, String version, Integer build
    ) {
        if (oldManifest != null && oldManifest.getCustomDownloadUrl() != null) {
            log.info("Using custom download URL before");
            return ExitCode.OK;
        }

        if (isSpecificVersion(version)) {
            if (build != null) {
                if (oldManifest == null) {
                    return logVersion(project, version, build);
                }
                if (mismatchingVersions(oldManifest, project, version, build)) {
                    return logMismatch("requested", oldManifest, project, version, build);
                }
            }
            else {
                return client.getLatestBuild(project, version)
                    .map(resolvedBuild -> {
                        if (oldManifest == null) {
                            return logVersion(project, version, resolvedBuild);
                        }
                        if (mismatchingVersions(oldManifest, project, version, resolvedBuild)) {
                            return logMismatch("resolved", oldManifest, project, version, resolvedBuild);
                        }
                        else {
                            return ExitCode.SOFTWARE;
                        }
                    })
                    .block();
            }
        }
        else {
            return client.getLatestVersionBuild(project)
                .map(versionBuild -> {
                    if (oldManifest == null) {
                        return logVersion(project, versionBuild.getVersion(), versionBuild.getBuild());
                    }
                    if (mismatchingVersions(oldManifest, project, versionBuild.getVersion(), versionBuild.getBuild())) {
                        return logMismatch("resolved", oldManifest, project, versionBuild.getVersion(), versionBuild.getBuild());
                    }
                    else {
                        return ExitCode.SOFTWARE;
                    }
                })
                .block();
        }
        return ExitCode.SOFTWARE;
    }

    private Integer logVersion(String project, String version, Integer build) {
        log.info("No previous installation. Would install {} version={} build={}",
            project, version, build);
        return ExitCode.OK;
    }

    private static int logMismatch(String inputType, PaperManifest oldManifest, String project, String version, Integer build) {
        log.info("Currently installed {} version={} build={}, {} {} version={} build={}",
            oldManifest.getProject(), oldManifest.getMinecraftVersion(), oldManifest.getBuild(),
            inputType,
            project, version, build
        );
        return ExitCode.OK;
    }

    private static boolean mismatchingVersions(PaperManifest oldManifest, String project, String version, Integer build) {
        return !Objects.equals(oldManifest.getProject(), project)
            && !Objects.equals(oldManifest.getMinecraftVersion(), version)
            && !Objects.equals(oldManifest.getBuild(), build);
    }

    private Mono<Result> downloadUsingCoordinates(PaperDownloadsClient client, String project,
        String version, Integer build
    ) {
        return
            assembleDownload(client, project, version, build)
            .map(result ->
                Result.builder()
                    .newManifest(
                        PaperManifest.builder()
                            .project(project)
                            .minecraftVersion(result.getVersion())
                            .build(result.getBuild())
                            .files(Collections.singleton(Manifests.relativize(outputDirectory, result.getFile())))
                            .build()
                    )
                    .serverJar(result.getFile())
                    .version(result.getVersion())
                    .build()
            );
    }

    private Mono<VersionBuildFile> assembleDownload(PaperDownloadsClient client, String project, String version,
        Integer build
    ) {
        final FileDownloadStatusHandler downloadStatusHandler = Fetch.loggingDownloadStatusHandler(log);

        if (isSpecificVersion(version)) {
            if (build != null) {
                return client.download(project, outputDirectory, downloadStatusHandler, version, build);
            }
            else {
                return client.downloadLatestBuild(project, outputDirectory, downloadStatusHandler, version);
            }
        }
        else {
            return client.downloadLatest(project, outputDirectory, downloadStatusHandler);
        }
    }

    private static boolean isSpecificVersion(String version) {
        return version != null && !version.equalsIgnoreCase("latest");
    }

    private Result downloadCustom(URI downloadUrl) {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-paper", sharedFetchArgs.options())) {
            return sharedFetch
                .fetch(downloadUrl)
                .toDirectory(outputDirectory)
                .skipUpToDate(true)
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .assemble()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(serverJar -> {
                    final String version;
                    try {
                        version = extractVersionFromJar(serverJar);

                        if (version == null) {
                            return Mono.error(new GenericException("Version metadata was not available from custom server jar"));
                        }
                    } catch (IOException e) {
                        return Mono.error(new GenericException("Failed to extract version from custom server jar", e));
                    }
                    return Mono.just(Result.builder()
                        .serverJar(serverJar)
                        .newManifest(
                            PaperManifest.builder()
                                .customDownloadUrl(downloadUrl)
                                .files(Collections.singleton(Manifests.relativize(outputDirectory, serverJar)))
                                .build()
                        )
                        .version(version)
                        .build());
                })
                .block();
        }
    }

    private String extractVersionFromJar(Path serverJar) throws IOException {
        final VersionMeta versionMeta = IoStreams.readFileFromZip(serverJar, "version.json", in ->
            ObjectMappers.defaultMapper().readValue(in, VersionMeta.class)
        );
        if (versionMeta == null) {
            return null;
        }

        return versionMeta.getId();
    }

    private PaperManifest loadOldManifest() {
        try {
            return Manifests.load(outputDirectory, PaperManifest.ID, PaperManifest.class);
        } catch (ManifestException e) {
            if (e.getCause() instanceof InvalidTypeIdException) {
                final MultiCopyManifest mcopyManifest = Manifests.load(outputDirectory, PaperManifest.ID,
                    MultiCopyManifest.class
                );
                if (mcopyManifest == null) {
                    throw new GenericException("Failed to load manifest as MultiCopyManifest");
                }
                return PaperManifest.builder()
                    .files(mcopyManifest.getFiles())
                    .build();
            }
            throw new GenericException("Failed to load manifest", e);
        }
    }

}
