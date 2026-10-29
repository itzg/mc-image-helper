package me.itzg.helpers.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.errors.RateLimitException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class DownloadAssetCommandTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .extensions(new ResponseTemplateTransformer(false))
        )
        .configureStaticDsl(true)
        .build();

    private final RandomStringUtils randomStringUtils = RandomStringUtils.insecure();

    @Test
    void usingNamePattern(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final String filename = randomStringUtils.nextAlphabetic(10) + ".jar";
        final String fileContent = randomStringUtils.nextAlphabetic(20);

        stubFor(get("/repos/org/repo/releases/latest")
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("github/release-with-sources-jar.json")
                .withTransformers("response-template")
                .withTransformerParameter("filename", filename)
            )
        );
        stubFor(head(urlPathEqualTo("/download/" + filename))
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
            )
        );
        stubFor(get("/download/" + filename)
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .withBody(fileContent)
            )
        );

        final int exitCode = new CommandLine(new GithubCommands())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "download-latest-asset",
                "--name-pattern", "app-.+?(?<!-sources)\\.jar",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve(filename))
            .exists()
            .content().isEqualTo(fileContent);
    }

    @Test
    void usingReleaseTag(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final String releaseTag = "1.2.3+mc1.21";
        final String filename = randomStringUtils.nextAlphabetic(10) + ".jar";
        final String fileContent = randomStringUtils.nextAlphabetic(20);

        stubFor(get("/repos/org/repo/releases/tags/" + releaseTag)
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("github/release-with-sources-jar.json")
                .withTransformers("response-template")
                .withTransformerParameter("filename", filename)
            )
        );
        stubFor(head(urlPathEqualTo("/download/" + filename))
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
            )
        );
        stubFor(get("/download/" + filename)
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .withBody(fileContent)
            )
        );

        final int exitCode = new CommandLine(new GithubCommands())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "download-asset",
                "--release-tag", releaseTag,
                "--name-pattern", "app-.+?(?<!-sources)\\.jar",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve(filename))
            .exists()
            .content().isEqualTo(fileContent);
    }

    @Test
    void usingLatestAsExplicitTag(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final String filename = randomStringUtils.nextAlphabetic(10) + ".jar";
        final String fileContent = randomStringUtils.nextAlphabetic(20);

        // an explicitly requested "latest" tag must resolve as a release tag,
        // distinct from omitting --release-tag (which uses the latest release)
        stubFor(get("/repos/org/repo/releases/tags/latest")
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("github/release-with-sources-jar.json")
                .withTransformers("response-template")
                .withTransformerParameter("filename", filename)
            )
        );
        stubFor(head(urlPathEqualTo("/download/" + filename))
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
            )
        );
        stubFor(get("/download/" + filename)
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .withBody(fileContent)
            )
        );

        final int exitCode = new CommandLine(new GithubCommands())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "download-asset",
                "--release-tag", "latest",
                "--name-pattern", "app-.+?(?<!-sources)\\.jar",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve(filename))
            .exists()
            .content().isEqualTo(fileContent);
    }

    @Test
    void releaseTagNotFound(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        stubFor(get("/repos/org/repo/releases/tags/missing")
            .willReturn(WireMock.notFound())
        );

        final int exitCode = new CommandLine(new GithubCommands())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "download-asset",
                "--release-tag", "missing",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void notFound(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        stubFor(get(anyUrl())
            .willReturn(WireMock.notFound())
        );

        final int exitCode = new CommandLine(new DownloadAssetCommand())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void rateLimitExceeded(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final Instant expectedDelayUntil = Instant.now()
            .plusSeconds(1);

        //noinspection UastIncorrectHttpHeaderInspection custom declared by Github
        stubFor(get(anyUrl())
            .willReturn(forbidden()
                .withHeader("x-ratelimit-reset", String.valueOf(expectedDelayUntil.getEpochSecond()))
            )
        );

        final LatchingExecutionExceptionHandler executionExceptionHandler = new LatchingExecutionExceptionHandler();

        final int exitCode = new CommandLine(new GithubCommands())
            .setExecutionExceptionHandler(executionExceptionHandler)
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "download-latest-asset",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);

        assertThat(executionExceptionHandler.getExecutionException())
            .isInstanceOf(RateLimitException.class)
            .hasFieldOrPropertyWithValue("delayUntil",
                expectedDelayUntil
                    // since the header is seconds-granularity
                    .truncatedTo(ChronoUnit.SECONDS)
            );
    }

    @Test
    void downloadsViaApiAssetUrlWithoutForwardingAuthorization(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final String filename = randomStringUtils.nextAlphabetic(10) + ".jar";
        final String fileContent = randomStringUtils.nextAlphabetic(20);
        final String token = "test-token";
        final String assetPath = "/repos/org/repo/releases/assets/42";
        final String storagePath = "/objects/" + filename;
        final String browserDownloadPath = "/download/should-not-be-used";

        final WireMockServer storage = new WireMockServer(wireMockConfig().dynamicPort());
        storage.start();
        try {
            stubFor(get("/repos/org/repo/releases/latest")
                .willReturn(ok()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("github/release-asset-api-redirect.json")
                    .withTransformers("response-template")
                    .withTransformerParameter("filename", filename)
                )
            );
            stubFor(any(urlPathEqualTo(assetPath))
                .willReturn(temporaryRedirect(storage.baseUrl() + storagePath))
            );
            stubFor(any(urlPathEqualTo(browserDownloadPath))
                .willReturn(serverError())
            );

            storage.stubFor(any(urlPathEqualTo(storagePath))
                .willReturn(ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .withBody(fileContent)
                )
            );

            final int exitCode = new CommandLine(new GithubCommands())
                .execute(
                    "--api-base-url", wmInfo.getHttpBaseUrl(),
                    "--token", token,
                    "download-asset",
                    "--output-directory", tempDir.toString(),
                    "org/repo"
                );

            assertThat(exitCode).isEqualTo(0);
            assertThat(tempDir.resolve(filename))
                .exists()
                .content().isEqualTo(fileContent);

            final List<LoggedRequest> apiRequests = findAll(anyRequestedFor(urlPathEqualTo(assetPath)));
            assertThat(apiRequests).isNotEmpty();
            assertThat(apiRequests).allSatisfy(request -> {
                assertThat(request.getHeader("Accept")).contains("application/octet-stream");
                assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + token);
            });

            final List<LoggedRequest> storageRequests = storage.findAll(anyRequestedFor(urlPathEqualTo(storagePath)));
            assertThat(storageRequests).isNotEmpty();
            assertThat(storageRequests).allSatisfy(request ->
                assertThat(request.getHeader("Authorization")).isNull()
            );

            assertThat(findAll(anyRequestedFor(urlPathEqualTo(browserDownloadPath)))).isEmpty();
        }
        finally {
            storage.stop();
        }
    }

}
