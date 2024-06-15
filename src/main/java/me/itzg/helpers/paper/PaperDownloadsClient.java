package me.itzg.helpers.paper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.paper.model.BuildInfo;
import me.itzg.helpers.paper.model.BuildInfo.DownloadInfo;
import me.itzg.helpers.paper.model.ProjectInfo;
import me.itzg.helpers.paper.model.ReleaseChannel;
import me.itzg.helpers.paper.model.VersionBuilds;
import reactor.core.publisher.Mono;

/**
 * <a href="https://api.papermc.io/docs/swagger-ui/index.html?configUrl=/openapi/swagger-config">Downloads API</a>
 */
@Slf4j
public class PaperDownloadsClient implements AutoCloseable{

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public PaperDownloadsClient(String baseUrl, SharedFetch.Options options) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        sharedFetch = Fetch.sharedFetch("install-paper", options);
    }

    private static <T> Iterable<T> reverse(List<T> versions) {
        final ArrayList<T> result = new ArrayList<>(versions);
        Collections.reverse(result);
        return result;
    }

    @Data
    @RequiredArgsConstructor
    public static class VersionBuild {
        final String version;
        final int build;
    }

    public Mono<VersionBuild> getLatestVersionBuild(String project, ReleaseChannel channel) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/projects/{project}", project)
            )
            .toObject(ProjectInfo.class)
            .assemble()
            .flatMapIterable(projectInfo -> reverse(projectInfo.getVersions()))
            .concatMap(v ->
                    getLatestBuild(project, v, channel)
                        .map(build -> new VersionBuild(v, build)),
                1
            )
            .next();
    }

    public Mono<Integer> getLatestBuild(String project, String version, ReleaseChannel channel) {
        log.debug("Retrieving latest build for project={}, version={}", project, version);

        return sharedFetch.fetch(
            uriBuilder.resolve("/v2/projects/{project}/versions/{version}/builds",
                project, version
                )
        )
            .toObject(VersionBuilds.class)
            .assemble()
            .flatMap(
                versionBuilds ->
                    Mono.justOrEmpty(
                    versionBuilds.getBuilds().stream()
                        // sort by build ID desc
                        .sorted(Comparator.comparingInt(BuildInfo::getBuild).reversed())
                        .filter(buildInfo -> buildInfo.getChannel() == channel)
                        .map(BuildInfo::getBuild)
                        .findFirst()
                    )
            );
    }

    public Mono<Path> download(String project, String version, int build, Path outputDirectory,
        FileDownloadStatusHandler downloadStatusHandler
    ) {
        return sharedFetch.fetch(
            uriBuilder.resolve("/v2/projects/{project}/versions/{version}/builds/{build}",
                project, version, build
                )
        )
            .toObject(BuildInfo.class)
            .assemble()
            .flatMap(buildInfo -> {
                    final DownloadInfo downloadInfo = buildInfo.getDownloads().get("application");
                    if (downloadInfo == null) {
                        return Mono.error(new GenericException(
                            String.format("Unable to locate application in download info for project=%s version=%s build=%d",
                                project, version, build
                                )));
                    }
                    return sharedFetch.fetch(
                        uriBuilder.resolve("/v2/projects/{project}/versions/{version}/builds/{build}/downloads/{download}",
                            project, version, build, downloadInfo.getName()
                            )
                    )
                        .toFile(outputDirectory.resolve(downloadInfo.getName()))
                        .skipExisting(true)
                        .handleStatus(downloadStatusHandler)
                        .assemble();
                });
    }

    @Override
    public void close() {
        sharedFetch.close();
    }
}
