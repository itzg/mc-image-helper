package me.itzg.helpers.paper;

import java.nio.file.Path;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.paper.model.BuildInfo;
import me.itzg.helpers.paper.model.BuildInfo.DownloadInfo;
import me.itzg.helpers.paper.model.ProjectInfo;
import me.itzg.helpers.paper.model.VersionInfo;
import reactor.core.publisher.Mono;

public class PaperDownloadsClient implements AutoCloseable{

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public PaperDownloadsClient(String baseUrl, SharedFetch.Options options) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        sharedFetch = Fetch.sharedFetch("install-paper", options);
    }

    public Mono<String> getLatestProjectVersion(String project) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/projects/{project}", project)
            )
            .toObject(ProjectInfo.class)
            .assemble()
            .map(projectInfo -> projectInfo.getVersions().get(projectInfo.getVersions().size() - 1));
    }

    public Mono<Boolean> hasVersion(String project, String version) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/projects/{project}", project)
            )
            .toObject(ProjectInfo.class)
            .assemble()
            .map(projectInfo -> projectInfo.getVersions().contains(version));
    }

    public Mono<Integer> getLatestBuild(String project, String version) {
        return sharedFetch.fetch(
            uriBuilder.resolve("/v2/projects/{project}/versions/{version}",
                project, version
                )
        )
            .toObject(VersionInfo.class)
            .assemble()
            .map(
                versionInfo -> versionInfo.getBuilds().get(versionInfo.getBuilds().size()-1)
            );
    }

    public Mono<Boolean> hasBuild(String project, String version, int build) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/projects/{project}/versions/{version}",
                    project, version
                )
            )
            .toObject(VersionInfo.class)
            .assemble()
            .map(versionInfo -> versionInfo.getBuilds().contains(build));
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
                        .skipUpToDate(true)
                        .handleStatus(downloadStatusHandler)
                        .assemble();
                });
    }

    @Override
    public void close() {
        sharedFetch.close();
    }
}
