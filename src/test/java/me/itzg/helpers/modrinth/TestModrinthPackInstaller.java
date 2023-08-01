package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import static org.junit.jupiter.api.Assertions.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;

import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestModrinthPackInstaller {
    @Test
    void installReturnsTheModpackIndexAndInstalledFiles(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        SharedFetchArgs fetchArgs = new SharedFetchArgs();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            fetchArgs.options());

        Path modpackPath = tempDir.resolve("test.mrpack");
        Path resultsFile = tempDir.resolve("results");

        ModpackIndex expectedIndex = createBasicModpackIndex();

        Files.write(modpackPath, createModrinthPack(expectedIndex, tempDir));

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchArgs, modpackPath, tempDir, resultsFile, false);

        ModrinthPackInstaller.Installation actualInstallation =
            installerUT.processModpack().block();

        assertNotNull(actualInstallation.getIndex());
        assertEquals(expectedIndex, actualInstallation.getIndex());
        assertEquals(0, actualInstallation.getFiles().size());
    }

    @Test
    void installDownloadsDependentFilesToInstallation(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        SharedFetchArgs fetchArgs = new SharedFetchArgs();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            fetchArgs.options());

        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        Path expectedFilePath = tempDir.resolve(relativeFilePath);
        Path resultsFile = tempDir.resolve("results");
        Path modpackPath = tempDir.resolve("test.mrpack");

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl()));

        Files.write(modpackPath, createModrinthPack(index, tempDir));

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchArgs, modpackPath, tempDir, resultsFile, false);

        List<Path> installedFiles =
            installerUT.processModpack().block().getFiles();

        assertTrue(Files.exists(expectedFilePath));

        String actualFileData =
            new String(Files.readAllBytes(expectedFilePath));

        assertEquals(expectedFileData, actualFileData);
        assertEquals(1, installedFiles.size());
        assertEquals(expectedFilePath, installedFiles.get(0));
    }
}
