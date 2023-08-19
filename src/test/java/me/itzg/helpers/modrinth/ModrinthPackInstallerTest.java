package me.itzg.helpers.modrinth;

import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public class ModrinthPackInstallerTest {
    @Test
    void installReturnsTheModpackIndexAndInstalledFiles(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        Options fetchOptions = new SharedFetchArgs().options();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOptions);

        Path modpackPath = tempDir.resolve("test.mrpack");
        Path resultsFile = tempDir.resolve("results");

        ModpackIndex expectedIndex = createBasicModpackIndex();

        Files.write(modpackPath, createModrinthPack(expectedIndex));

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchOptions, modpackPath, tempDir, resultsFile, false);

        Installation actualInstallation =
            installerUT.processModpack().block();

        assertThat(actualInstallation).isNotNull();
        assertThat(actualInstallation.getIndex()).isNotNull();
        assertThat(actualInstallation.getIndex()).isEqualTo(expectedIndex);
        assertThat(actualInstallation.getFiles().size()).isEqualTo(0);
    }

    @Test
    void installDownloadsDependentFilesToInstallation(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        Options fetchOpts = new SharedFetchArgs().options();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        Path expectedFilePath = tempDir.resolve(relativeFilePath);
        Path resultsFile = tempDir.resolve("results");
        Path modpackPath = tempDir.resolve("test.mrpack");

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl()));

        Files.write(modpackPath, createModrinthPack(index));

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false);

        final Installation installation = installerUT.processModpack().block();
        assertThat(installation).isNotNull();
        List<Path> installedFiles = installation.getFiles();

        assertThat(expectedFilePath).isRegularFile();
        assertThat(expectedFilePath).content()
            .isEqualTo(expectedFileData);
        assertThat(installedFiles.size()).isEqualTo(1);
        assertThat(installedFiles.get(0)).isEqualTo(expectedFilePath);
    }
}
