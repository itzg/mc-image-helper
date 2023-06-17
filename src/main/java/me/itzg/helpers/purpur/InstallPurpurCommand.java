package me.itzg.helpers.purpur;

import static me.itzg.helpers.errors.Validators.DESCRIPTION_MINECRAFT_VERSION;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.errors.Validators;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.ManifestException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.purpur.model.VersionMeta;
import me.itzg.helpers.sync.MultiCopyManifest;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "install-purpur", description = "Downloads latest or selected version of Purpur")
@Slf4j
public class InstallPurpurCommand implements Callable<Integer> {

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

            @Option(names = "--version", defaultValue = "latest", description = DESCRIPTION_MINECRAFT_VERSION)
            public void setVersion(String version) {
                this.version = Validators.validateMinecraftVersion(spec, version);
            }
            String version;

            @Option(names = "--build")
            public void setBuild(String build) {
                if (build != null && build.equalsIgnoreCase("latest")) {
                    this.build = null;
                }
                else {
                    this.build = build;
                }
            }
            String build;
        }
    }

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".")
    Path outputDirectory;

    @Option(names = "--base-url", defaultValue = "https://api.purpurmc.org")
    String baseUrl;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @ArgGroup
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Builder
    private static class Result {

        final PurpurManifest newManifest;
        final Path serverJar;
        final String version;
    }

    @Override
    public Integer call() throws Exception {
        final PurpurManifest oldManifest = loadOldManifest();

        final Result result;
        try (PurpurDownloadsClient client = new PurpurDownloadsClient(baseUrl, sharedFetchArgs.options())) {
            if (inputs.downloadUrl != null) {
                result = downloadCustom(inputs.downloadUrl);
            }
            else {
                result = useCoordinates(client,
                    inputs.coordinates.version, inputs.coordinates.build
                );
            }
        }

        if (resultsFile != null) {
            try (ResultsFileWriter resultsWriter = new ResultsFileWriter(resultsFile)) {
                resultsWriter.writeServer(result.serverJar);
                resultsWriter.writeType("PURPUR");
                resultsWriter.writeVersion(result.version);
            }
        }

        Manifests.cleanup(outputDirectory, oldManifest, result.newManifest, log);
        Manifests.save(outputDirectory, PurpurManifest.ID, result.newManifest);

        return ExitCode.OK;
    }

    private Result useCoordinates(PurpurDownloadsClient client, String version, String build) {
        return resolveVersion(client, version)
            .flatMap(v -> resolveBuild(client, v, build)
                .flatMap(b -> {
                        log.info("Resolved Purpur version {} build {}", v, b);

                        return client.download(v, b, outputDirectory, Fetch.loggingDownloadStatusHandler(log))
                            .map(serverJar ->
                                Result.builder()
                                    .newManifest(
                                        PurpurManifest.builder()
                                            .minecraftVersion(v)
                                            .build(b)
                                            .files(Collections.singleton(Manifests.relativize(outputDirectory, serverJar)))
                                            .build()
                                    )
                                    .serverJar(serverJar)
                                    .version(v)
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
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .assemble()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(serverJar -> {
                    @SuppressWarnings("DuplicatedCode") // same as Paper
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
                            PurpurManifest.builder()
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

    private PurpurManifest loadOldManifest() {
        try {
            return Manifests.load(outputDirectory, PurpurManifest.ID, PurpurManifest.class);
        } catch (ManifestException e) {
            if (e.getCause() instanceof InvalidTypeIdException) {
                final MultiCopyManifest mcopyManifest = Manifests.load(outputDirectory, PurpurManifest.ID,
                    MultiCopyManifest.class
                );
                if (mcopyManifest == null) {
                    throw new GenericException("Failed to load manifest as MultiCopyManifest");
                }
                return PurpurManifest.builder()
                    .files(mcopyManifest.getFiles())
                    .build();
            }
            throw new GenericException("Failed to load manifest", e);
        }
    }

    private Mono<String> resolveVersion(PurpurDownloadsClient client, String version) {
        if (version.equals("latest")) {
            return client.getLatestVersion();
        }
        return client.hasVersion(version)
            .flatMap(exists -> exists ? Mono.just(version) : Mono.error(() -> new InvalidParameterException(
                String.format("Version %s does not exist", version)
            )));
    }

    private Mono<String > resolveBuild(PurpurDownloadsClient client, String version, String build) {
        if (build == null) {
            return client.getLatestBuild(version);
        }
        else {
            return client.hasBuild(version, build)
                .flatMap(exists -> exists ? Mono.just(build) : Mono.error(() ->
                    new InvalidParameterException(String.format("Build %s does not exist for version %s", build, version))
                ));
        }
    }
}
