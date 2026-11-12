package me.itzg.helpers.hangar;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class HangarCommandTest {

    final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
        )
        .configureStaticDsl(true)
        .build();

    @Test
    void downloadsLatestVersionOfChannel(@TempDir Path tempDir) {
        stubLatestVersion("Maintenance", "PAPER", "1.21.4", "Release",
            version("5.1.1", "PAPER", hostedDownload("Maintenance-Paper-5.1.1.jar", "maintenance content"))
        );
        stubFile("Maintenance-Paper-5.1.1.jar", "maintenance content");

        final int exitCode = execute(tempDir, "--projects", "Maintenance");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/Maintenance-Paper-5.1.1.jar")).hasContent("maintenance content");
        assertThat(Manifests.buildManifestPath(tempDir, HangarManifest.ID)).exists();
    }

    @Test
    void usesRequestedChannelAndPlatform(@TempDir Path tempDir) {
        stubLatestVersion("ViaVersion", "VELOCITY", "3.4.0", "Snapshot",
            version("5.12.3-SNAPSHOT+1119", "VELOCITY", hostedDownload("ViaVersion-5.12.3-SNAPSHOT.jar", "snapshot"))
        );
        stubFile("ViaVersion-5.12.3-SNAPSHOT.jar", "snapshot");

        final int exitCode = new CommandLine(new HangarCommand())
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--game-version", "3.4.0",
                "--platform", "VELOCITY",
                "--channel", "Snapshot",
                "--projects", "ViaVersion"
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/ViaVersion-5.12.3-SNAPSHOT.jar")).hasContent("snapshot");
    }

    @Test
    void downloadsRequestedVersion(@TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/api/v1/projects/ViaVersion/versions/5.12.2"))
            .willReturn(okJson(
                version("5.12.2", "PAPER", hostedDownload("ViaVersion-5.12.2.jar", "pinned")).toString()
            ))
        );
        stubFile("ViaVersion-5.12.2.jar", "pinned");

        final int exitCode = execute(tempDir, "--projects", "ViaVersion:5.12.2");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/ViaVersion-5.12.2.jar")).hasContent("pinned");
    }

    @Test
    void downloadsExternalFile(@TempDir Path tempDir) {
        final ObjectNode externalDownload = objectMapper.createObjectNode()
            .put("externalUrl", wm.getRuntimeInfo().getHttpBaseUrl() + "/external/floodgate");
        stubLatestVersion("Floodgate", "PAPER", "1.21.4", "Release",
            version("Floodgate", "PAPER", externalDownload)
        );
        stubFor(head(urlPathEqualTo("/external/floodgate"))
            .willReturn(ok()
                .withHeader("Content-Disposition", "attachment; filename=\"floodgate-spigot.jar\"")
            )
        );
        stubFor(get(urlPathEqualTo("/external/floodgate"))
            .willReturn(ok()
                .withHeader("Content-Disposition", "attachment; filename=\"floodgate-spigot.jar\"")
                .withBody("floodgate")
            )
        );

        final int exitCode = execute(tempDir, "--projects", "Floodgate");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/floodgate-spigot.jar")).hasContent("floodgate");
    }

    @Test
    void replacesExistingFileWithSameNameButDifferentContent(@TempDir Path tempDir) throws Exception {
        final Path existing = Files.createDirectories(tempDir.resolve("plugins"))
            .resolve("ViaVersion-5.12.3-SNAPSHOT.jar");
        Files.write(existing, "previous build".getBytes(StandardCharsets.UTF_8));

        stubLatestVersion("ViaVersion", "PAPER", "1.21.4", "Release",
            version("5.12.3-SNAPSHOT+1119", "PAPER", hostedDownload("ViaVersion-5.12.3-SNAPSHOT.jar", "newer build"))
        );
        stubFile("ViaVersion-5.12.3-SNAPSHOT.jar", "newer build");

        final int exitCode = execute(tempDir, "--projects", "ViaVersion");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(existing).hasContent("newer build");
        verify(1, getRequestedFor(urlPathEqualTo("/cdn/ViaVersion-5.12.3-SNAPSHOT.jar")));
    }

    @Test
    void failsWhenNoVersionMatches(@TempDir Path tempDir) {
        stubLatestVersion("Maintenance", "PAPER", "1.21.4", "Release");
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();

        final int exitCode = execute(exceptionHandler, tempDir, "--projects", "Maintenance");

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(exceptionHandler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("does not have any versions in channel Release");
    }

    @Test
    void failsWhenProjectIsMissing(@TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/api/v1/projects/missing/versions"))
            .willReturn(notFound())
        );
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();

        final int exitCode = execute(exceptionHandler, tempDir, "--projects", "missing");

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(exceptionHandler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("could not be located on Hangar");
    }

    @Test
    void failsWhenVersionHasNoDownloadForPlatform(@TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/api/v1/projects/ViaVersion/versions/5.12.2"))
            .willReturn(okJson(
                version("5.12.2", "PAPER", hostedDownload("ViaVersion-5.12.2.jar", "paper only")).toString()
            ))
        );
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();

        final int exitCode = execute(exceptionHandler, tempDir,
            "--platform", "VELOCITY",
            "--projects", "ViaVersion:5.12.2"
        );

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(exceptionHandler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("does not have a download for platform VELOCITY");
    }

    @Test
    void skipsOptionalProjectThatIsMissing(@TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/api/v1/projects/missing/versions"))
            .willReturn(notFound())
        );
        stubLatestVersion("Maintenance", "PAPER", "1.21.4", "Release",
            version("5.1.1", "PAPER", hostedDownload("Maintenance-Paper-5.1.1.jar", "maintenance content"))
        );
        stubFile("Maintenance-Paper-5.1.1.jar", "maintenance content");

        final int exitCode = execute(tempDir, "--projects", "missing?,Maintenance");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/Maintenance-Paper-5.1.1.jar")).exists();
    }

    @Test
    void removesFilesOfProjectsNoLongerRequested(@TempDir Path tempDir) {
        stubLatestVersion("Maintenance", "PAPER", "1.21.4", "Release",
            version("5.1.1", "PAPER", hostedDownload("Maintenance-Paper-5.1.1.jar", "maintenance content"))
        );
        stubFile("Maintenance-Paper-5.1.1.jar", "maintenance content");
        stubLatestVersion("ViaVersion", "PAPER", "1.21.4", "Release",
            version("5.12.2", "PAPER", hostedDownload("ViaVersion-5.12.2.jar", "viaversion content"))
        );
        stubFile("ViaVersion-5.12.2.jar", "viaversion content");

        assertThat(execute(tempDir, "--projects", "Maintenance,ViaVersion")).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/ViaVersion-5.12.2.jar")).exists();

        assertThat(execute(tempDir, "--projects", "Maintenance")).isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("plugins/Maintenance-Paper-5.1.1.jar")).exists();
        assertThat(tempDir.resolve("plugins/ViaVersion-5.12.2.jar")).doesNotExist();
    }

    private int execute(Path tempDir, String... extraArgs) {
        return execute(new LatchingExecutionExceptionHandler(), tempDir, extraArgs);
    }

    private int execute(LatchingExecutionExceptionHandler exceptionHandler, Path tempDir, String... extraArgs) {
        final ArrayList<String> args = new ArrayList<>(Arrays.asList(
            "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
            "--output-directory", tempDir.toString(),
            "--game-version", "1.21.4"
        ));
        args.addAll(Arrays.asList(extraArgs));

        return new CommandLine(new HangarCommand())
            .setExecutionExceptionHandler(exceptionHandler)
            .execute(args.toArray(new String[0]));
    }

    private void stubLatestVersion(String project, String platform, String gameVersion, String channel,
        ObjectNode... versions) {
        final ObjectNode paginated = objectMapper.createObjectNode();
        final ArrayNode result = paginated.putArray("result");
        for (final ObjectNode version : versions) {
            result.add(version);
        }

        stubFor(get(urlPathEqualTo("/api/v1/projects/" + project + "/versions"))
            .withQueryParam("channel", equalTo(channel))
            .withQueryParam("platform", equalTo(platform))
            .withQueryParam("platformVersion", equalTo(gameVersion))
            .withQueryParam("limit", equalTo("1"))
            .willReturn(okJson(paginated.toString()))
        );
    }

    private void stubFile(String filename, String content) {
        stubFor(get(urlPathEqualTo("/cdn/" + filename))
            .willReturn(ok().withBody(content))
        );
    }

    private ObjectNode version(String name, String platform, ObjectNode download) {
        final ObjectNode version = objectMapper.createObjectNode()
            .put("name", name);
        version.putObject("downloads")
            .set(platform, download);
        return version;
    }

    private ObjectNode hostedDownload(String filename, String content) {
        final ObjectNode download = objectMapper.createObjectNode()
            .put("downloadUrl", wm.getRuntimeInfo().getHttpBaseUrl() + "/cdn/" + filename);
        download.putObject("fileInfo")
            .put("name", filename)
            .put("sha256Hash", DigestUtils.sha256Hex(content));
        return download;
    }
}
