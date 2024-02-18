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
import me.itzg.helpers.errors.RateLimitException;
import me.itzg.helpers.github.model.Asset;
import me.itzg.helpers.github.model.Release;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

@Slf4j
public class GithubClient {

    public static final String DEFAULT_API_BASE_URL = "https://api.github.com";

    private final SharedFetch sharedFetch;
    private final UriBuilder uriBuilder;

    public GithubClient(SharedFetch sharedFetch, String apiBaseUrl) {
        this.sharedFetch = sharedFetch;
        this.uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);
    }

    public Mono<Path> downloadLatestAsset(String org, String repo, @Nullable Pattern namePattern, Path outputDirectory) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/repos/{org}/{repo}/releases/latest", org, repo)
            )
            .acceptContentTypes(Collections.singletonList("application/vnd.github+json"))
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
}
