package me.itzg.helpers.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.http.Fetch.fetch;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
class OutputToDirectoryFetchBuilderTest {

    @Test
    void basicScenario(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        stubFor(
            head(WireMock.urlPathEqualTo("/file"))
                .willReturn(
                    ok()
                        .withHeader("content-disposition", "attachment; filename=\"actual.txt\"")
                )
        );
        stubFor(
            get("/file")
                .willReturn(
                    ok("content of actual.txt")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/file"))
            .toDirectory(tempDir)
            .execute();

        final Path expectedFile = tempDir.resolve("actual.txt");

        assertThat(result)
            .isEqualTo(expectedFile)
            .exists()
            .hasContent("content of actual.txt");
    }

    @Test
    void bukkitDoesNotSupportHead(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        final String body = RandomStringUtils.randomAlphabetic(10);

        stubFor(head(urlEqualTo("/projects/worldedit/files/latest"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/error?aspxerrorpath=/projects/worldedit/files/latest")
            )
        );
        stubFor(head(urlEqualTo("/error?aspxerrorpath=/projects/worldedit/files/latest"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
            )
        );
        stubFor(get(urlEqualTo("/projects/worldedit/files/latest"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/files/4586/220/worldedit-bukkit-7.2.15.jar?api-key=267C6CA3")
            )
        );
        stubFor(get(urlEqualTo("/files/4586/220/worldedit-bukkit-7.2.15.jar?api-key=267C6CA3"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/files/4586/220/worldedit-bukkit-7.2.15.jar")
            )
        );
        stubFor(get(urlEqualTo("/files/4586/220/worldedit-bukkit-7.2.15.jar"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(body)
            )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/projects/worldedit/files/latest"))
            .toDirectory(tempDir)
            .skipUpToDate(true)
            .skipExisting(false)
            .assemble()
            .block();

        final Path expectedFile = tempDir.resolve("worldedit-bukkit-7.2.15.jar");

        assertThat(result)
            .isEqualTo(expectedFile);

        assertThat(expectedFile)
            .exists()
            .content().isEqualTo(body);
    }

    @Test
    void githubReleaseFile(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        final String body = RandomStringUtils.randomAlphabetic(10);

        stubFor(head(urlEqualTo("/itzg/mc-image-helper/releases/download/1.35.2/mc-image-helper-1.35.2.zip"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/github-production-release-asset-2e65be/404894256/1c8e6f58-29ba-4f91-9a84-1412ae23c469?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20230930%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230930T004153Z&X-Amz-Expires=300&X-Amz-Signature=d5d268534f6a40dad718c9dee9e81090b2e23d1602f6e679750870c2c051c4e2&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=404894256&response-content-disposition=attachment%3B%20filename%3Dmc-image-helper-1.35.2.zip&response-content-type=application%2Foctet-stream")
            )
        );
        stubFor(head(urlEqualTo("/github-production-release-asset-2e65be/404894256/1c8e6f58-29ba-4f91-9a84-1412ae23c469?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20230930%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230930T004153Z&X-Amz-Expires=300&X-Amz-Signature=d5d268534f6a40dad718c9dee9e81090b2e23d1602f6e679750870c2c051c4e2&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=404894256&response-content-disposition=attachment%3B%20filename%3Dmc-image-helper-1.35.2.zip&response-content-type=application%2Foctet-stream"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Disposition", "attachment; filename=mc-image-helper-1.35.2.zip")
            )
        );
        stubFor(get(urlEqualTo("/github-production-release-asset-2e65be/404894256/1c8e6f58-29ba-4f91-9a84-1412ae23c469?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20230930%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230930T004153Z&X-Amz-Expires=300&X-Amz-Signature=d5d268534f6a40dad718c9dee9e81090b2e23d1602f6e679750870c2c051c4e2&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=404894256&response-content-disposition=attachment%3B%20filename%3Dmc-image-helper-1.35.2.zip&response-content-type=application%2Foctet-stream"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Disposition", "attachment; filename=mc-image-helper-1.35.2.zip")
                .withBody(body)
            )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/itzg/mc-image-helper/releases/download/1.35.2/mc-image-helper-1.35.2.zip"))
            .toDirectory(tempDir)
            .skipUpToDate(true)
            .skipExisting(false)
            .assemble()
            .block();

        final Path expectedFile = tempDir.resolve("mc-image-helper-1.35.2.zip");

        assertThat(result)
            .isEqualTo(expectedFile);

        assertThat(expectedFile)
            .exists()
            .content().isEqualTo(body);

    }

    @Test
    void geyser(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        final String body = RandomStringUtils.randomAlphabetic(10);

        stubFor(head(urlEqualTo("/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("location", "/v2/projects/geyser/versions/2.2.0/builds/310/downloads/spigot")
            )
        );
        stubFor(head(urlEqualTo("/v2/projects/geyser/versions/2.2.0/builds/310/downloads/spigot"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-disposition", "attachment; filename=\"=?UTF-8?Q?Geyser-Spigot.jar?=\"; filename*=UTF-8''Geyser-Spigot.jar")
            )
        );
        stubFor(get(urlEqualTo("/v2/projects/geyser/versions/2.2.0/builds/310/downloads/spigot"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-disposition", "attachment; filename=\"=?UTF-8?Q?Geyser-Spigot.jar?=\"; filename*=UTF-8''Geyser-Spigot.jar")
                .withBody(body)
            )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"))
            .toDirectory(tempDir)
            .skipUpToDate(true)
            .skipExisting(false)
            .assemble()
            .block();

        final Path expectedFile = tempDir.resolve("Geyser-Spigot.jar");

        assertThat(result)
            .isEqualTo(expectedFile);

        assertThat(expectedFile)
            .exists()
            .content().isEqualTo(body);
    }

    @Test
    void geyserSkipsUpToDate(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final String body = RandomStringUtils.randomAlphabetic(10);
        final Path expectedFile = Files.createFile(tempDir.resolve("Geyser-Spigot.jar"));
        Files.write(expectedFile, body.getBytes(StandardCharsets.UTF_8));

        stubFor(head(urlEqualTo("/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"))
            .willReturn(aResponse()
                .withStatus(302)
                .withHeader("location", "/v2/projects/geyser/versions/2.2.0/builds/310/downloads/spigot")
            )
        );
        stubFor(head(urlEqualTo("/v2/projects/geyser/versions/2.2.0/builds/310/downloads/spigot"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-disposition", "attachment; filename=\"=?UTF-8?Q?Geyser-Spigot.jar?=\"; filename*=UTF-8''Geyser-Spigot.jar")
                .withHeader("last-modified", formattedLastModified(expectedFile))
            )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"))
            .toDirectory(tempDir)
            .skipUpToDate(true)
            .skipExisting(false)
            .assemble()
            .block();

        assertThat(result)
            .isEqualTo(expectedFile);

        assertThat(expectedFile)
            .exists()
            .content().isEqualTo(body);
    }

    @NotNull
    private static String formattedLastModified(Path expectedFile) throws IOException {
        return FetchBuilderBase.httpDateTimeFormatter.format(Files.getLastModifiedTime(expectedFile).toInstant());
    }

    @Test
    void supportsIfModifiedSince(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final String body = RandomStringUtils.randomAlphabetic(10);
        final Path expectedFile = Files.createFile(tempDir.resolve("result.jar"));
        Files.write(expectedFile, body.getBytes(StandardCharsets.UTF_8));

        stubFor(head(urlEqualTo("/result.jar"))
            .willReturn(aResponse()
                .withStatus(200)
            )
        );
        stubFor(get(urlEqualTo("/result.jar"))
            .withHeader("If-Modified-Since", equalTo(formattedLastModified(expectedFile)))
            .willReturn(aResponse()
                .withStatus(304)
            )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/result.jar"))
            .toDirectory(tempDir)
            .skipUpToDate(true)
            .skipExisting(false)
            .assemble()
            .block();

        assertThat(result)
            .isEqualTo(expectedFile);

        assertThat(expectedFile)
            .exists()
            .content().isEqualTo(body);
    }

    /**
     * Sonatype Nexus only provides the options "attachment" and "inline" for the content-disposition header.
     * To accommodate this, make sure that these invalid header values do not cause a total failure of the download.
     */
    @Test
    void tolerateInvalidContentDispositionFileName(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        stubFor(
            head(WireMock.urlPathEqualTo("/actual.txt"))
                .willReturn(
                    ok().withHeader("content-disposition", "attachment")
                )
                .willReturn(
                    ok().withHeader("content-disposition", "inline")
                )
        );
        stubFor(
            get("/actual.txt")
                .willReturn(
                    ok("content of actual.txt")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/actual.txt"))
            .toDirectory(tempDir)
            .execute();

        // resolves to actual file name because of invalid content-disposition
        final Path expectedFile = tempDir.resolve("actual.txt");

        assertThat(result)
            .isEqualTo(expectedFile)
            .exists()
            .hasContent("content of actual.txt");
    }
}