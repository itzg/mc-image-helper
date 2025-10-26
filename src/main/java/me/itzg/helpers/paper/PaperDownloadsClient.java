package me.itzg.helpers.paper;

import java.net.URI;
import java.nio.file.Path;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.paper.model.BuildResponse;
import me.itzg.helpers.paper.model.Channel;
import me.itzg.helpers.paper.model.Download;
import me.itzg.helpers.paper.model.ProjectResponse;
import me.itzg.helpers.paper.model.VersionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <a href="https://fill.papermc.io/swagger-ui/index.html">Implements PaperMC's Fill Downloads API v3</a>
 */
@Slf4j
public class PaperDownloadsClient implements AutoCloseable{

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public PaperDownloadsClient(String baseUrl, SharedFetch.Options options) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        sharedFetch = Fetch.sharedFetch("install-paper", options);
    }

    @Data
    public static class VersionBuild {
        final String version;
        final int build;
    }

    @Data
    public static class VersionBuildFile {
        final String version;
        final int build;
        final Path file;
    }

    public Mono<VersionBuild> getLatestVersionBuild(String project, RequestedChannel requestedChannel) {
        return getProjectVersions(project)
            .flatMap(projectResponse ->
                extractLatestVersionBuild(project, requestedChannel, projectResponse)
            );
    }

    public Mono<Integer> getLatestBuild(String project, String version) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v3/projects/{project}/versions/{version}/builds/latest",
                    project, version
                )
            )
            .toObject(BuildResponse.class)
            .assemble()
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable -> new InvalidParameterException(
                    String.format("Requested version %s is not available", version))
            )
            .map(BuildResponse::getId);
    }

    public Mono<VersionBuildFile> downloadLatest(String project, RequestedChannel requestedChannel,
        Path outputDirectory, FileDownloadStatusHandler downloadStatusHandler
    ) {
        return getProjectVersions(project)
            .flatMap(projectResponse ->
                extractLatestVersionBuild(project, requestedChannel, projectResponse)
                    .flatMap(versionBuild ->
                        download(project, outputDirectory, downloadStatusHandler,
                            versionBuild.getVersion(),
                            versionBuild.getBuild()
                        )
                    )
            );
    }

    public Mono<VersionBuildFile> downloadLatestBuild(String project,
        Path outputDirectory, FileDownloadStatusHandler downloadStatusHandler,
        String version
    ) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v3/projects/{project}/versions/{version}/builds/latest",
                    project, version
                )
            )
            .toObject(BuildResponse.class)
            .assemble()
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable -> new InvalidParameterException(
                    String.format("Requested version %s is not available", version))
            )
            .flatMap(buildResponse ->
                downloadWithBuildResponse(outputDirectory, downloadStatusHandler, version, buildResponse)
                    .map(path -> new VersionBuildFile(version, buildResponse.getId(), path))
            );

    }

    public Mono<VersionBuildFile> download(String project,
        Path outputDirectory, FileDownloadStatusHandler downloadStatusHandler,
        String version,
        int build
    ) {
        return getBuild(project, version, build)
            .flatMap(buildResponse ->
                downloadWithBuildResponse(outputDirectory, downloadStatusHandler, version, buildResponse)
                    .map(path -> new VersionBuildFile(version, build, path))
            );
    }

    private Mono<BuildResponse> getBuild(String project, String version, int build) {

        return sharedFetch.fetch(
                uriBuilder.resolve("/v3/projects/{project}/versions/{version}/builds/{build}",
                    project, version, build
                )
            )
            .toObject(BuildResponse.class)
            .assemble()
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable -> new InvalidParameterException(
                    String.format("Requested version %s, build %d is not available", version, build))
            );
    }

    private Mono<ProjectResponse> getProjectVersions(String project) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v3/projects/{project}/versions", project)
            )
            .toObject(ProjectResponse.class)
            .assemble()
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable -> new InvalidParameterException(
                    String.format("Requested project %s does not exist", project))
            );
    }

    @RequiredArgsConstructor
    private static class VersionAndBuildResponse {
        final VersionResponse versionResponse;
        final BuildResponse buildResponse;
    }

    private Mono<VersionBuild> extractLatestVersionBuild(String project, RequestedChannel requestedChannel, ProjectResponse projectResponse) {
        if (projectResponse.getVersions() == null ||
            projectResponse.getVersions().isEmpty()) {
            log.warn("No versions found for project={}", project);
            return Mono.error(() -> new InvalidParameterException("No versions found for project"));
        }

        return Flux.fromIterable(projectResponse.getVersions())
            .filter(versionResponse -> versionResponse.getBuilds() != null && !versionResponse.getBuilds().isEmpty())
            .concatMap(versionResponse ->
                getBuild(project, versionResponse.getVersion().getId(), getLatestBuild(versionResponse))
                    .map(buildResponse -> new VersionAndBuildResponse(versionResponse, buildResponse))
                )
            .takeUntil(vAndB -> acceptableChannel(vAndB.buildResponse.getChannel(), requestedChannel))
            .last()
            .map(vAndB -> new VersionBuild(vAndB.versionResponse.getVersion().getId(), vAndB.buildResponse.getId()));
    }

    private static Integer getLatestBuild(VersionResponse versionResponse) {
        return versionResponse.getBuilds().stream()
            .max(Integer::compare)
            .orElseThrow(() -> new GenericException("No builds found for version " + versionResponse.getVersion().getId()));
    }

    private boolean acceptableChannel(Channel channel, RequestedChannel requestedChannel) {
        for (final Channel mapped : requestedChannel.getMappedTo()) {
            if (mapped.equals(channel)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Path> downloadWithBuildResponse(Path outputDirectory, FileDownloadStatusHandler downloadStatusHandler,
        String version, BuildResponse buildResponse) {
        // TODO confirm channel
        final Download download = buildResponse.getDownloads().get("server:default");
        if (download == null) {
            return Mono.error(new GenericException(
                String.format("Unable to locate server download for version=%s build=%d",
                    version, buildResponse.getId())
            ));
        }

        return downloadFile(outputDirectory, downloadStatusHandler, download);
    }

    private Mono<Path> downloadFile(Path outputDirectory,
        FileDownloadStatusHandler downloadStatusHandler,
        Download download
    ) {
        return sharedFetch.fetch(URI.create(download.getUrl()))
            .toFile(outputDirectory.resolve(download.getName()))
            .skipExisting(true)
            .handleStatus(downloadStatusHandler)
            .assemble();
    }

    @Override
    public void close() {
        sharedFetch.close();
    }
}
