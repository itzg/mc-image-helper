package me.itzg.helpers.hangar;

import static me.itzg.helpers.http.Uris.QueryParameters.queryParameters;

import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.hangar.model.Platform;
import me.itzg.helpers.hangar.model.Version;
import me.itzg.helpers.hangar.model.VersionsResponse;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * Provides a client for the <a href="https://hangar.papermc.io/api-docs">Hangar API</a>
 */
public class HangarApiClient {

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public HangarApiClient(String baseUrl, SharedFetch sharedFetch) {
        this.uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        this.sharedFetch = sharedFetch;
    }

    /**
     * @return the newest version in the channel that supports the platform and game version
     * or an empty mono if there is none
     */
    public Mono<Version> getLatestVersion(String slugOrId, Platform platform, String gameVersion, String channel) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/api/v1/projects/{slugOrId}/versions",
                    queryParameters()
                        .add("channel", channel)
                        .add("platform", platform.name())
                        .add("platformVersion", gameVersion)
                        .add("limit", "1"),
                    slugOrId
                )
            )
            .toObject(VersionsResponse.class)
            .assemble()
            .onErrorMap(FailedRequestException::isNotFound, throwable ->
                InvalidParameterException.formatted("Project %s could not be located on Hangar", slugOrId)
            )
            .mapNotNull(resp ->
                resp.getResult() == null || resp.getResult().isEmpty() ? null : resp.getResult().get(0)
            );
    }

    public Mono<Version> getVersion(String slugOrId, String versionName) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/api/v1/projects/{slugOrId}/versions/{nameOrId}", slugOrId, versionName)
            )
            .toObject(Version.class)
            .assemble()
            .onErrorMap(FailedRequestException::isNotFound, throwable ->
                InvalidParameterException.formatted("Version %s of project %s could not be located on Hangar",
                    versionName, slugOrId
                )
            );
    }
}
