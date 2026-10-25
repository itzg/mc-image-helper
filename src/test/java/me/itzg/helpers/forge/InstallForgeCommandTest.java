package me.itzg.helpers.forge;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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

    private static byte[] installerBody;

    @BeforeAll
    static void buildExecutableInstaller() throws IOException {
        installerBody = executableInstaller();
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
    void forceReinstallDownloadsAgain(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubInstaller(INSTALLER_PATH, "application/java-archive");
        final String url = installerUrl(wm, INSTALLER_PATH);

        assertThat(run(tempDir, "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);
        resetAllRequests();

        assertThat(run(tempDir, "--force-reinstall", "--forge-installer-url", url))
            .isEqualTo(ExitCode.OK);

        verify(1, getRequestedFor(urlEqualTo(INSTALLER_PATH)));
        verify(1, anyRequestedFor(anyUrl()));
        assertThat(Manifests.load(tempDir, "forge", ForgeManifest.class).getInstallerUrl())
            .isEqualTo(url);
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
        stubFor(get(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", contentType)
                .withBody(installerBody)
            )
        );
    }

    /**
     * The trimmed fixture has no Main-Class. Copy it and add one so the installer process can exit successfully
     * while version.json stays the original entry.
     */
    private static byte[] executableInstaller() throws IOException {
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
                    zip.putNextEntry(new ZipEntry(entry.getName()));
                    source.transferTo(zip);
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
}
