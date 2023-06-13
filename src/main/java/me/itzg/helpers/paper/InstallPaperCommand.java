package me.itzg.helpers.paper;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.ManifestException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatus;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.sync.MultiCopyManifest;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import reactor.core.publisher.Mono;

@Command(name = "install-paper", description = "Installs selected PaperMC")
@Slf4j
public class InstallPaperCommand implements Callable<Integer> {

    @ArgGroup
    Inputs inputs = new Inputs();

    private static void logDownloadingStatus(FileDownloadStatus status, URI uri, Path file) {
        switch (status) {
            case DOWNLOADING:
                log.debug("Downloading {}", file);
                break;
            case DOWNLOADED:
                log.info("Downloaded {}", file);
                break;
            case SKIP_FILE_UP_TO_DATE:
                log.info("The file {} is already up to date", file);
                break;
        }
    }

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

            private static final Pattern ALLOWED_VERSIONS = Pattern.compile("latest|\\d+\\.\\d+\\.\\d+",
                Pattern.CASE_INSENSITIVE
            );

            @Option(names = "--version", defaultValue = "latest", description = "May be 'latest' or specific version")
            public void setVersion(String version) {
                final Matcher m = ALLOWED_VERSIONS.matcher(version);
                if (!m.matches()) {
                    throw new ParameterException(spec.commandLine(), "Invalid value for minecraft version: " + version);
                }
                this.version = version.toLowerCase();

            }

            String version;

            @Option(names = "--build")
            Integer build;
        }
    }

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".")
    Path outputDirectory;

    @Option(names = "--base-url", defaultValue = "https://api.papermc.io")
    String baseUrl;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @ArgGroup
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Builder
    private static class Result {

        final PaperManifest newManifest;
        final Path serverJar;
    }

    @Override
    public Integer call() throws Exception {
        final PaperManifest oldManifest = loadOldManifest();

        final Result result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(baseUrl, sharedFetchArgs.options())) {
            if (inputs.downloadUrl != null) {
                result = downloadCustom(inputs.downloadUrl);
            }
            else {
                result = useCoordinates(client, inputs.coordinates.project,
                    inputs.coordinates.version, inputs.coordinates.build
                );
            }
        }

        if (resultsFile != null) {
            try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
                results.writeServer(result.serverJar);
            }
        }

        Manifests.cleanup(outputDirectory, oldManifest, result.newManifest, log);
        Manifests.save(outputDirectory, PaperManifest.ID, result.newManifest);

        return ExitCode.OK;
    }

    private Result useCoordinates(PaperDownloadsClient client, String project, String version, Integer build) {
        return resolveVersion(client, project, version)
            .flatMap(v -> resolveBuild(client, project, v, build)
                .flatMap(b -> {
                        log.info("Resolved {} to version {} build {}", project, v, b);

                        return client.download(project, v, b, outputDirectory, InstallPaperCommand::logDownloadingStatus)
                            .map(serverJar ->
                                Result.builder()
                                    .newManifest(
                                        PaperManifest.builder()
                                            .project(project)
                                            .minecraftVersion(v)
                                            .build(b)
                                            .files(Collections.singleton(Manifests.relativize(outputDirectory, serverJar)))
                                            .build()
                                    )
                                    .serverJar(serverJar)
                                    .build()
                            );
                    }
                )
            )
            .block();
    }

    private Result downloadCustom(URI downloadUrl) {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-paper", sharedFetchArgs.options())) {
            return sharedFetch
                .fetch(downloadUrl)
                .toDirectory(outputDirectory)
                .skipUpToDate(true)
                .handleStatus(InstallPaperCommand::logDownloadingStatus)
                .assemble()
                .map(serverJar ->
                    Result.builder()
                        .serverJar(serverJar)
                        .newManifest(
                            PaperManifest.builder()
                                .customDownloadUrl(downloadUrl)
                                .files(Collections.singleton(Manifests.relativize(outputDirectory, serverJar)))
                                .build()
                        )
                        .build()
                )
                .block();
        }
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

    private Mono<String> resolveVersion(PaperDownloadsClient client, String project, String version) {
        if (version.equals("latest")) {
            return client.getLatestProjectVersion(project);
        }
        return client.hasVersion(project, version)
            .flatMap(exists -> exists ? Mono.just(version) : Mono.error(() -> new InvalidParameterException(
                String.format("Version %s does not exist for the project %s",
                    version, project
                ))));
    }

    private Mono<Integer> resolveBuild(PaperDownloadsClient client, String project, String version, Integer build) {
        if (build == null) {
            return client.getLatestBuild(project, version);
        }
        else {
            return client.hasBuild(project, version, build)
                .flatMap(exists -> exists ? Mono.just(build) : Mono.error(() ->
                    new InvalidParameterException(String.format("Build %d does not exist for project %s version %s",
                        build, project, version
                    )
                    )
                ));
        }
    }
}
