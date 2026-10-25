package me.itzg.helpers.forge;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import me.itzg.helpers.files.Manifests;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.ParameterException;

@WireMockTest
class InstallForgeCommandTest {

    private static final Path TRIMMED_INSTALLER = Path.of(
        "src/test/resources/forge/forge-1.20.2-48.1.0-installer-trimmed.jar"
    );
    private static final String INSTALLER_PATH = "/forge-1.20.2-48.1.0-installer-trimmed.jar";

    private static final String ORIGINAL_VERSION_ID = "\"id\": \"1.20.2-forge-48.1.0\"";
    private static final String ORIGINAL_INHERITS_FROM = "\"inheritsFrom\": \"1.20.2\"";

    private static byte[] installerBody;
    private static byte[] alternateInstallerBody;

    @BeforeAll
    static void buildExecutableInstaller() throws IOException {
        installerBody = executableInstaller("1.20.2", "48.1.0");
        alternateInstallerBody = executableInstaller("1.19.4", "45.2.0");
    }

    @Test
    void freshInstallDownloadsOnceAndRecordsUrl(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubInstaller(INSTALLER_PATH, "application/octet-stream");
        final String url = installerUrl(wm, INSTALLER_PATH);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        verify(1, getRequestedFor(urlEqualTo(INSTALLER_PATH)));
        verify(1, anyRequestedFor(anyUrl()));

        final ForgeManifest manifest = Manifests.load(tempDir, "forge", ForgeManifest.class);
        assertThat(manifest).isNotNull();
        assertThat(manifest.getInstallerUrl()).isEqualTo(url);
        assertThat(manifest.getMinecraftVersion()).isEqualTo("1.20.2");
        assertThat(manifest.getForgeVersion()).isEqualTo("48.1.0");
        assertThat(tempDir.resolve(manifest.getServerEntry())).exists();
        assertThat(tempDir.resolve(INSTALLER_PATH.substring(1))).doesNotExist();
    }

    @Test
    void secondRunWithSameUrlMakesNoRequests(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final String url = installerUrl(wm, INSTALLER_PATH);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);
        assertThat(tempDir.resolve("forge-1.20.2-48.1.0.jar")).exists();

        resetAllRequests();

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void differentUrlDownloadsAgain(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final String otherPath = "/other-installer.jar";
        stubInstaller(otherPath, "application/octet-stream");

        final String firstUrl = installerUrl(wm, INSTALLER_PATH);
        final String otherUrl = installerUrl(wm, otherPath);

        assertThat(run(tempDir, "--forge-installer-url", firstUrl))
            .isEqualTo(ExitCode.OK);
        resetAllRequests();

        assertThat(run(tempDir, "--forge-installer-url", otherUrl))
            .isEqualTo(ExitCode.OK);

        verify(1, getRequestedFor(urlEqualTo(otherPath)));
        verify(0, getRequestedFor(urlEqualTo(INSTALLER_PATH)));
        assertThat(Manifests.load(tempDir, "forge", ForgeManifest.class).getInstallerUrl())
            .isEqualTo(otherUrl);
    }

    @Test
    void forceReinstallDownloadsAgain(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final String url = installerUrl(wm, INSTALLER_PATH);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);
        resetAllRequests();

        assertThat(Files.readString(tempDir.resolve("installer-runs.txt"))).isEqualTo("ran\n");

        assertThat(run(tempDir, "--force-reinstall", "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        verify(1, getRequestedFor(urlEqualTo(INSTALLER_PATH)));
        verify(1, anyRequestedFor(anyUrl()));
        assertThat(Manifests.load(tempDir, "forge", ForgeManifest.class).getInstallerUrl())
            .isEqualTo(url);
        assertThat(Files.readString(tempDir.resolve("installer-runs.txt"))).isEqualTo("ran\nran\n");
    }

    @Test
    void existingFileWithInstallerBasenameSurvives(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final String path = "/server.jar";
        stubInstaller(path, "application/java-archive");
        final Path existing = tempDir.resolve("server.jar");
        Files.writeString(existing, "keep-this-server");

        assertThat(run(tempDir, "--forge-installer-url", installerUrl(wm, path)))
            .isEqualTo(ExitCode.OK);

        assertThat(Files.readString(existing)).isEqualTo("keep-this-server");
        assertThat(tempDir.resolve("forge-1.20.2-48.1.0.jar")).exists();
    }

    @Test
    void missingServerEntryRefreshesVersionFromSameUrl(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final String url = installerUrl(wm, INSTALLER_PATH);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        final ForgeManifest installed = Manifests.load(tempDir, "forge", ForgeManifest.class);
        assertThat(installed.getMinecraftVersion()).isEqualTo("1.20.2");
        assertThat(installed.getForgeVersion()).isEqualTo("48.1.0");
        Files.delete(tempDir.resolve(installed.getServerEntry()));

        reset();
        stubInstaller(INSTALLER_PATH, "application/java-archive", alternateInstallerBody);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        verify(1, getRequestedFor(urlEqualTo(INSTALLER_PATH)));
        final ForgeManifest refreshed = Manifests.load(tempDir, "forge", ForgeManifest.class);
        assertThat(refreshed.getInstallerUrl()).isEqualTo(url);
        assertThat(refreshed.getMinecraftVersion()).isEqualTo("1.19.4");
        assertThat(refreshed.getForgeVersion()).isEqualTo("45.2.0");
        assertThat(tempDir.resolve(refreshed.getServerEntry())).exists();
    }

    @Test
    void createsMissingOutputDirectory(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final Path outputDir = tempDir.resolve("server");
        assertThat(outputDir).doesNotExist();

        assertThat(run(outputDir, "--forge-installer-url", installerUrl(wm, INSTALLER_PATH)))
            .isEqualTo(ExitCode.OK);

        assertThat(outputDir).isDirectory();
        final ForgeManifest manifest = Manifests.load(outputDir, "forge", ForgeManifest.class);
        assertThat(manifest).isNotNull();
        assertThat(manifest.getMinecraftVersion()).isEqualTo("1.20.2");
        assertThat(manifest.getForgeVersion()).isEqualTo("48.1.0");
        assertThat(outputDir.resolve(manifest.getServerEntry())).exists();
    }

    @Test
    void rejectsUrlCombinedWithOtherInstallerOptions() {
        assertThatThrownBy(() -> new CommandLine(new InstallForgeCommand()).parseArgs(
            "--forge-installer-url", "https://example.com/forge-installer.jar",
            "--forge-version", "48.1.0"
        ))
            .isInstanceOf(ParameterException.class);

        assertThatThrownBy(() -> new CommandLine(new InstallForgeCommand()).parseArgs(
            "--forge-installer-url", "https://example.com/forge-installer.jar",
            "--forge-installer", "forge-installer.jar"
        ))
            .isInstanceOf(ParameterException.class);
    }

    @Test
    void installerUrlNotFoundGivesClearError(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws Exception {
        final String path = "/missing-installer.jar";
        stubFor(get(urlEqualTo(path))
            .willReturn(aResponse().withStatus(404))
        );
        final String url = installerUrl(wm, path);

        final String err = tapSystemErr(() ->
            assertThat(run(tempDir, "--forge-installer-url", url))
                .isEqualTo(ExitCode.SOFTWARE)
        );

        assertThat(err).contains(url);
        assertThat(err).contains("404");
        assertThat(Manifests.load(tempDir, "forge", ForgeManifest.class)).isNull();
    }

    @Test
    void loadsManifestWrittenBeforeInstallerUrl(@TempDir Path tempDir) throws IOException {
        final ForgeManifest manifest = ForgeManifest.builder()
            .minecraftVersion("1.20.2")
            .forgeVersion("48.1.0")
            .serverEntry("forge.jar")
            .build();
        Manifests.save(tempDir, "forge", manifest);

        final Path manifestFile = tempDir.resolve(".forge-manifest.json");
        assertThat(Files.readString(manifestFile)).doesNotContain("installerUrl");

        Files.writeString(manifestFile, """
            {
              "@type": "me.itzg.helpers.forge.ForgeManifest",
              "timestamp": "2024-01-01T00:00:00Z",
              "minecraftVersion": "1.16.5",
              "forgeVersion": "36.2.34",
              "serverEntry": "forge.jar"
            }
            """);

        final ForgeManifest loaded = Manifests.load(tempDir, "forge", ForgeManifest.class);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getInstallerUrl()).isNull();
        assertThat(loaded.getMinecraftVersion()).isEqualTo("1.16.5");
        assertThat(loaded.getForgeVersion()).isEqualTo("36.2.34");
        assertThat(loaded.getServerEntry()).isEqualTo("forge.jar");
    }

    private static int run(Path tempDir, String... args) {
        final String[] command = new String[args.length + 2];
        command[0] = "--output-directory";
        command[1] = tempDir.toString();
        System.arraycopy(args, 0, command, 2, args.length);
        return new CommandLine(new InstallForgeCommand()).execute(command);
    }

    private static String installerUrl(WireMockRuntimeInfo wm, String path) {
        return wm.getHttpBaseUrl() + path;
    }

    private static void stubInstaller(String path, String contentType) {
        stubInstaller(path, contentType, installerBody);
    }

    private static void stubInstaller(String path, String contentType, byte[] body) {
        stubFor(get(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", contentType)
                .withBody(body)
            )
        );
    }

    /**
     * The trimmed fixture has no Main-Class. Copy it and add one so the installer process can exit successfully.
     * version.json is rewritten when a test needs a different Minecraft or Forge version.
     */
    private static byte[] executableInstaller(String minecraftVersion, String forgeVersion) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write((
                "Manifest-Version: 1.0\r\n"
                    + "Main-Class: me.itzg.helpers.forge.TrimmedForgeInstaller\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            try (ZipInputStream source = new ZipInputStream(Files.newInputStream(TRIMMED_INSTALLER))) {
                ZipEntry entry;
                while ((entry = source.getNextEntry()) != null) {
                    final byte[] contents = source.readAllBytes();
                    zip.putNextEntry(new ZipEntry(entry.getName()));
                    if ("version.json".equals(entry.getName())) {
                        zip.write(rewriteVersionJson(contents, minecraftVersion, forgeVersion));
                    } else {
                        zip.write(contents);
                    }
                    zip.closeEntry();
                }
            }

            zip.putNextEntry(new ZipEntry("me/itzg/helpers/forge/TrimmedForgeInstaller.class"));
            try (InputStream classBytes = TrimmedForgeInstaller.class.getResourceAsStream("TrimmedForgeInstaller.class")) {
                if (classBytes == null) {
                    throw new IOException("TrimmedForgeInstaller class bytes were not found");
                }
                classBytes.transferTo(zip);
            }
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private static byte[] rewriteVersionJson(byte[] contents, String minecraftVersion, String forgeVersion) {
        final String json = new String(contents, StandardCharsets.UTF_8)
            .replace(ORIGINAL_VERSION_ID, "\"id\": \"" + minecraftVersion + "-forge-" + forgeVersion + "\"")
            .replace(ORIGINAL_INHERITS_FROM, "\"inheritsFrom\": \"" + minecraftVersion + "\"");
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
