package me.itzg.helpers.purpur;

import java.nio.file.Path;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.purpur.model.ProjectInfo;
import me.itzg.helpers.purpur.model.VersionInfo;
import reactor.core.publisher.Mono;

public class PurpurDownloadsClient implements AutoCloseable{

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public PurpurDownloadsClient(String baseUrl, SharedFetch.Options options) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        sharedFetch = Fetch.sharedFetch("install-paper", options);
    }

    public Mono<String> getLatestVersion() {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/purpur")
            )
            .toObject(ProjectInfo.class)
            .assemble()
            .map(projectInfo -> projectInfo.getVersions().get(projectInfo.getVersions().size() - 1));
    }

    public Mono<Boolean> hasVersion(String version) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/purpur")
            )
            .toObject(ProjectInfo.class)
            .assemble()
            .map(projectInfo -> projectInfo.getVersions().contains(version));
    }

    public Mono<String> getLatestBuild(String version) {
        return sharedFetch.fetch(
            uriBuilder.resolve("/v2/purpur/{version}", version)
        )
            .toObject(VersionInfo.class)
            .assemble()
            .map(
                versionInfo -> versionInfo.getBuilds().getLatest()
            );
    }

    public Mono<Boolean> hasBuild(String version, String build) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/purpur/{version}",
                    version
                )
            )
            .toObject(VersionInfo.class)
            .assemble()
            .map(versionInfo -> versionInfo.getBuilds().getAll().contains(build));
    }

    public Mono<Path> download(String version, String build, Path outputDirectory,
        FileDownloadStatusHandler downloadStatusHandler
    ) {
        return sharedFetch.fetch(
            uriBuilder.resolve("/v2/purpur/{version}/{build}/download", version, build)
        )
            .toDirectory(outputDirectory)
            .handleStatus(downloadStatusHandler)
            .skipUpToDate(true)
            .assemble();
    }

    @Override
    public void close() {
        sharedFetch.close();
    }
}
