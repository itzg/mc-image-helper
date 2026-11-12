package me.itzg.helpers.paper;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.netty.channel.ConnectTimeoutException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.paper.PaperDownloadsClient.VersionBuildFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import reactor.core.publisher.Mono;

@WireMockTest
class InstallPaperCommandTest {

    private static final String JAR_NAME = "paper-1.21.6-46.jar";
    private static final String JAR_CONTENT = "existing-paper-jar";
    private static final String VERSION = "1.21.6";
    private static final int BUILD = 46;

    @Test
    void fallsBackToExistingInstallWhenApiUnreachable(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final byte[] manifestBefore = manifestBytes(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION, "--build=" + BUILD))
                .isEqualTo(ExitCode.OK)
        );

        assertKeptExisting(tempDir, jar, resultsFile, manifestBefore, err);
    }

    @Test
    void fallsBackWhenRequestedVersionIsLatest(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final byte[] manifestBefore = manifestBytes(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=latest"))
                .isEqualTo(ExitCode.OK)
        );

        assertKeptExisting(tempDir, jar, resultsFile, manifestBefore, err);
    }

    @Test
    void fallsBackWhenConnectTimesOut(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final byte[] manifestBefore = manifestBytes(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(connectTimeout(), tempDir, "http://192.0.2.1", resultsFile, "--version=" + VERSION))
                .isEqualTo(ExitCode.OK)
        );

        assertKeptExisting(tempDir, jar, resultsFile, manifestBefore, err);
        assertThat(err).contains("ConnectTimeoutException");
    }

    @Test
    void connectTimeoutWithoutExistingInstallStillFails(@TempDir Path tempDir) throws Exception {
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(connectTimeout(), tempDir, "http://192.0.2.1", resultsFile, "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).contains("ConnectTimeoutException");
        assertThat(err).doesNotContain("keeping existing");
        assertThat(resultsFile).doesNotExist();
        assertThat(Manifests.buildManifestPath(tempDir, PaperManifest.ID)).doesNotExist();
    }

    @Test
    void failsWhenApiUnreachableWithoutExistingInstall(@TempDir Path tempDir) throws Exception {
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(resultsFile).doesNotExist();
        assertThat(Manifests.buildManifestPath(tempDir, PaperManifest.ID)).doesNotExist();
    }

    @Test
    void failsWhenManifestExistsButJarIsMissing(@TempDir Path tempDir) throws Exception {
        saveManifest(tempDir, "paper", VERSION, BUILD, JAR_NAME);
        final Path resultsFile = tempDir.resolve("results.env");

        assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION))
            .isNotEqualTo(ExitCode.OK);

        assertThat(resultsFile).doesNotExist();
        assertThat(tempDir.resolve(JAR_NAME)).doesNotExist();
    }

    @Test
    void doesNotFallBackWhenRequestedVersionDiffers(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final byte[] manifestBefore = manifestBytes(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=1.20.4"))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(manifestBytes(tempDir)).isEqualTo(manifestBefore);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void doesNotFallBackWhenRequestedBuildDiffers(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION, "--build=47"))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void doesNotFallBackWhenRequestedProjectDiffers(@TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--project=folia", "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void doesNotFallBackWhenPaperApiReturnsNotFound(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws Exception {
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/latest"))
            .willReturn(aResponse().withStatus(404))
        );
        final Path jar = installExisting(tempDir);
        final byte[] manifestBefore = manifestBytes(tempDir);
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, wm.getHttpBaseUrl(), resultsFile, "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(manifestBytes(tempDir)).isEqualTo(manifestBefore);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void doesNotFallBackWhenInstalledProjectIsMissing(@TempDir Path tempDir) throws Exception {
        final Path jar = tempDir.resolve(JAR_NAME);
        Files.writeString(jar, JAR_CONTENT);
        Manifests.save(tempDir, PaperManifest.ID, PaperManifest.builder()
            .minecraftVersion(VERSION)
            .build(BUILD)
            .files(Collections.singletonList(JAR_NAME))
            .build()
        );
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void doesNotFallBackOverCustomUrlManifest(@TempDir Path tempDir) throws Exception {
        final Path jar = tempDir.resolve(JAR_NAME);
        Files.writeString(jar, JAR_CONTENT);
        Manifests.save(tempDir, PaperManifest.ID, PaperManifest.builder()
            .project("paper")
            .minecraftVersion(VERSION)
            .build(BUILD)
            .customDownloadUrl(URI.create("https://example.invalid/folia.jar"))
            .files(Collections.singletonList(JAR_NAME))
            .build()
        );
        final Path resultsFile = tempDir.resolve("results.env");

        final String err = tapSystemErr(() ->
            assertThat(execute(tempDir, closedBaseUrl(), resultsFile, "--version=" + VERSION))
                .isNotEqualTo(ExitCode.OK)
        );

        assertThat(err).doesNotContain("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(resultsFile).doesNotExist();
    }

    @Test
    void localWriteFailureDoesNotFallBack(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws Exception {
        final Path jar = installExisting(tempDir);
        final String downloadName = "paper-1.21.6-99.jar";
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/latest"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": 99,
                      "channel": "STABLE",
                      "downloads": {
                        "server:default": {
                          "name": "%s",
                          "url": "%s/%s"
                        }
                      }
                    }
                    """.formatted(downloadName, wm.getHttpBaseUrl(), downloadName))
            )
        );
        stubFor(get(urlPathEqualTo("/" + downloadName))
            .willReturn(aResponse().withBody("new-jar"))
        );
        final Path resultsFile = tempDir.resolve("results.env");
        final Set<PosixFilePermission> original = Files.getPosixFilePermissions(tempDir);
        Files.setPosixFilePermissions(tempDir, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE
        ));
        try {
            final String err = tapSystemErr(() ->
                assertThat(execute(tempDir, wm.getHttpBaseUrl(), resultsFile, "--version=" + VERSION))
                    .isNotEqualTo(ExitCode.OK)
            );

            assertThat(err).doesNotContain("keeping existing");
            assertThat(err).contains("AccessDeniedException");
            assertThat(jar).hasContent(JAR_CONTENT);
            assertThat(resultsFile).doesNotExist();
            assertThat(tempDir.resolve(downloadName)).doesNotExist();
        } finally {
            Files.setPosixFilePermissions(tempDir, original);
        }
    }

    private static void assertKeptExisting(Path tempDir, Path jar, Path resultsFile, byte[] manifestBefore, String err)
        throws IOException {
        assertThat(err).contains("keeping existing");
        assertThat(jar).hasContent(JAR_CONTENT);
        assertThat(manifestBytes(tempDir)).isEqualTo(manifestBefore);
        assertThat(resultsFile).content().contains(
            "SERVER=\"" + jar + "\"",
            "VERSION=\"" + VERSION + "\"",
            "TYPE=\"PAPER\""
        );
        try (Stream<Path> listing = Files.list(tempDir)) {
            assertThat(listing.map(path -> path.getFileName().toString()))
                .noneMatch(name -> name.endsWith(".download"));
        }
    }

    private static Path installExisting(Path outputDirectory) throws IOException {
        final Path jar = outputDirectory.resolve(JAR_NAME);
        Files.writeString(jar, JAR_CONTENT);
        saveManifest(outputDirectory, "paper", VERSION, BUILD, JAR_NAME);
        return jar;
    }

    private static void saveManifest(Path outputDirectory, String project, String minecraftVersion, int build, String jarName) {
        Manifests.save(outputDirectory, PaperManifest.ID, PaperManifest.builder()
            .project(project)
            .minecraftVersion(minecraftVersion)
            .build(build)
            .files(Collections.singletonList(jarName))
            .build()
        );
    }

    private static byte[] manifestBytes(Path outputDirectory) throws IOException {
        return Files.readAllBytes(Manifests.buildManifestPath(outputDirectory, PaperManifest.ID));
    }

    private static InstallPaperCommand connectTimeout() {
        return new InstallPaperCommand() {
            @Override
            Mono<VersionBuildFile> assembleDownload(
                PaperDownloadsClient client, String project, String version,
                Integer build, RequestedChannel channel
            ) {
                return Mono.error(new ConnectTimeoutException(
                    "connection timed out after 30000 ms: fill.papermc.io/104.18.19.103:443"
                ));
            }
        };
    }

    private static String closedBaseUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static int execute(Path outputDirectory, String baseUrl, Path resultsFile, String... extraArgs) {
        return execute(new InstallPaperCommand(), outputDirectory, baseUrl, resultsFile, extraArgs);
    }

    private static int execute(
        InstallPaperCommand command, Path outputDirectory, String baseUrl, Path resultsFile, String... extraArgs
    ) {
        final List<String> args = new ArrayList<>();
        args.add("--output-directory");
        args.add(outputDirectory.toString());
        args.add("--base-url");
        args.add(baseUrl);
        args.add("--http-response-timeout=PT1S");
        if (resultsFile != null) {
            args.add("--results-file");
            args.add(resultsFile.toString());
        }
        Collections.addAll(args, extraArgs);
        // Production enables this so the channel default "default" matches RequestedChannel.DEFAULT.
        return new CommandLine(command)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args.toArray(new String[0]));
    }
}
