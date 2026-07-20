package me.itzg.helpers.github;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.errors.RateLimitException;
import me.itzg.helpers.github.model.Artifact;
import me.itzg.helpers.github.model.ArtifactsResponse;
import me.itzg.helpers.github.model.Asset;
import me.itzg.helpers.github.model.Release;
import me.itzg.helpers.github.model.WorkflowRunsResponse;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.http.Uris.QueryParameters;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class GithubClient {

    public static final String DEFAULT_API_BASE_URL = "https://api.github.com";
    private static final int PAGE_SIZE = 100;

    private final SharedFetch sharedFetch;
    private final UriBuilder uriBuilder;
    private final String token;

    public GithubClient(SharedFetch sharedFetch, String apiBaseUrl, @Nullable String token) {
        this.sharedFetch = sharedFetch;
        this.uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);
        this.token = token;
    }

    public Mono<Path> downloadLatestAsset(String org, String repo, @Nullable Pattern namePattern, Path outputDirectory) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/repos/{org}/{repo}/releases/latest", org, repo)
            )
            .acceptContentTypes(Collections.singletonList("application/vnd.github+json"))
            .withAuthorization("Bearer", token)
            .toObject(Release.class)
            .assemble()
            .onErrorResume(throwable -> {
                if (throwable instanceof FailedRequestException) {
                    final FailedRequestException fre = (FailedRequestException) throwable;
                    if (fre.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                        return Mono.empty();
                    }

                    if ((fre.getStatusCode() == FORBIDDEN.code() || fre.getStatusCode() == TOO_MANY_REQUESTS.code())) {
                        final HttpHeaders headers = fre.getHeaders();
                        final String resetTimeStr = headers.get("x-ratelimit-reset");
                        if (resetTimeStr != null) {
                            return Mono.error(new RateLimitException(Instant.ofEpochSecond(Long.parseLong(resetTimeStr)),
                                "Rate-limit exceeded", fre
                                ));
                        }
                    }
                }
                return Mono.error(throwable);
            })
            .flatMap(release -> {
                if (log.isDebugEnabled()) {
                    log.debug("Assets in latest release '{}': {}",
                        release.getName(),
                        release.getAssets().stream()
                            .map(Asset::getName)
                            .collect(Collectors.toList())
                        );
                }

                return Mono.justOrEmpty(
                        release.getAssets().stream()
                            .filter(asset -> namePattern == null || namePattern.matcher(asset.getName()).matches())
                            .findFirst()
                    )
                    .flatMap(asset ->
                        sharedFetch.fetch(URI.create(asset.getBrowserDownloadUrl()))
                            .toDirectory(outputDirectory)
                            .skipExisting(true)
                            .assemble()
                    );
            });
    }

    /**
     * Querys a repositorys workflow for an artifact matching a pattern
     *
     * @param org         Organisation to query repository
     * @param repo        Repository to query workflow
     * @param workflow    Workflow to search for successful runs
     * @param namePattern Regular expression {@link Pattern} to match artifact name
     * @return Artifact containing download URL
     */
    public Mono<Artifact> resolveArtifactForLatestSuccessfulWorkflow(String org, String repo,
            String workflow, Pattern namePattern) {
        return sharedFetch.fetch(
                uriBuilder.resolve(
                        "/repos/{org}/{repo}/actions/workflows/{workflow}/runs",
                        QueryParameters.queryParameters()
                                .add("status", "success")
                                .add("per_page", "1"),
                        org, repo, workflow))
                .withAuthorization("Bearer", token)
                .toObject(WorkflowRunsResponse.class)
                .assemble()
                .mapNotNull(resp -> resp.getWorkflowRuns().stream().findFirst().orElse(null))
                .flatMap(run -> resolveArtifactForRun(org, repo, run.getId(), namePattern));
    }

    /**
     * Querys a workflows artifacts for matching artifact.
     *
     * Lists all artifacts for a workflow run, then filters by a {@link Pattern}.
     * Pattern must resolve one Artifact
     *
     * @param org         Organisation to query repository
     * @param repo        Repository to query workflow
     * @param runId       Specific Workflow run to query Artifacts
     * @param namePattern Regular expression {@link Pattern} to match artifact name
     * @return            Artifact matching pattern
     */
    public Mono<Artifact> resolveArtifactForRun(String org, String repo, long runId, Pattern namePattern) {
        return listWorkflowRunArtifacts(org, repo, runId, 1)
                .filter(artifact -> namePattern.matcher(artifact.getName()).matches())
                .collectList()
                .flatMap(matches -> {
                    if (matches.isEmpty()) {
                        return Mono.empty();
                    }
                    if (matches.size() > 1) {
                        return Mono.error(InvalidParameterException.formatted(
                                "Multiple artifacts in workflow run %d matched pattern '%s': %s",
                                runId,
                                namePattern,
                                matches.stream().map(Artifact::getName).collect(Collectors.joining(", "))));
                    }

                    final Artifact artifact = matches.get(0);
                    if (artifact.isExpired()) {
                        return Mono.error(GenericException.formatted(
                                "Artifact '%s' from workflow run %d expired at %s",
                                artifact.getName(), runId, artifact.getExpiresAt()));
                    }
                    return Mono.just(artifact);
                });
    }

    /**
     * Downloads artifact to output
     *
     * @param artifact   File to download from GitHub
     * @param outputFile Path to download file to
     * @return Path to output file
     */
    public Mono<Path> downloadArtifact(Artifact artifact, Path outputFile) {
        return sharedFetch.fetch(URI.create(artifact.getArchiveDownloadUrl()))
                .withAuthorization("Bearer", token)
                .toFile(outputFile)
                .assemble();
    }

    /**
     * Lists artifacts for successful GitHub actions run.
     *
     * Recursively calls GitHub api to fetch artifacts for a specified run
     *
     * @param org   Organisation to query repository
     * @param repo  Repository to query workflow
     * @param runId Specific Workflow run to query Artifacts
     * @param page  Page of api response to query
     * @return List of {@link me.itzg.helpers.github.model.Artifact}
     */
    private Flux<Artifact> listWorkflowRunArtifacts(String org, String repo, long runId, int page) {
        return sharedFetch.fetch(
                uriBuilder.resolve(
                        "/repos/{org}/{repo}/actions/runs/{runId}/artifacts",
                        QueryParameters.queryParameters()
                                .add("per_page", String.valueOf(PAGE_SIZE))
                                .add("page", String.valueOf(page)),
                        org, repo, runId))
                .withAuthorization("Bearer", token)
                .toObject(ArtifactsResponse.class)
                .assemble()
                .flatMapMany(response -> {
                    final Flux<Artifact> artifacts = Flux.fromIterable(response.getArtifacts());
                    if ((long) page * PAGE_SIZE < response.getTotalCount()) {
                        return artifacts.concatWith(listWorkflowRunArtifacts(org, repo, runId, page + 1));
                    }
                    return artifacts;
                });
    }
}
