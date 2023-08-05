package me.itzg.helpers.modrinth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.modrinth.model.*;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;

@WireMockTest
public class InstallModrinthModpackCommandTest {
    private String projectName = "test_project1";
    private String projectId = "efgh5678";
    private String projectVersionId = "abcd1234";
    private Version projectVersion =
        createModrinthProjectVersion(projectVersionId);

    static InstallModrinthModpackCommand createInstallModrinthModpackCommand(
        String baseUrl, Path outputDir, String projectName, String versionId,
        ModpackLoader loader)
    {
        InstallModrinthModpackCommand commandUT =
            new InstallModrinthModpackCommand();
        commandUT.baseUrl = baseUrl;
        commandUT.outputDirectory = outputDir;
        commandUT.modpackProject = projectName;
        commandUT.version = versionId;
        commandUT.loader = loader;

        return commandUT;
    }

    @Test
    void downloadsAndInstallsModrinthModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge);

        int commandStatus = commandUT.call();

        assertEquals(0, commandStatus);

        String actualFileData =
            new String(Files.readAllBytes(tempDir.resolve(relativeFilePath)));

        assertEquals(expectedFileData, actualFileData);
    }

    @Test
    void createsModrinthModpackManifestForModpackInstallation(
                WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException, URISyntaxException
    {
        ModpackIndex index = createBasicModpackIndex();

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge);

        int commandStatus = commandUT.call();

        assertEquals(0, commandStatus);

        ModrinthModpackManifest installedManifest = Manifests.load(tempDir,
            ModrinthModpackManifest.ID, ModrinthModpackManifest.class);

        assertNotNull(installedManifest);
        assertEquals(projectName, installedManifest.getProjectSlug());
        assertEquals(projectVersionId, installedManifest.getVersionId());
        assertEquals(0, installedManifest.getFiles().size());
        assertEquals(
            index.getDependencies(), installedManifest.getDependencies());
    }

    @Test
    void removesFilesNoLongerNeedeByUpdatedModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
            projectName, projectVersionId, ModpackLoader.forge)
            .call();

        String newProjectVersionId = "1234abcd";
        Version newProjectVersion =
            createModrinthProjectVersion(newProjectVersionId);
        index.getFiles().remove(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, newProjectVersion,
            createModrinthPack(index));

        int commandStatus =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, newProjectVersionId, ModpackLoader.forge)
                .call();

        assertEquals(0, commandStatus);
        assertFalse(Files.exists(tempDir.resolve(relativeFilePath)));
    }

    @Test
    void downloadsAndInstallsGenericModpacksOverHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        String modpackDownloadPath = "/files/modpacks/test_modpack-1.0.0.mrpack";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubFor(get(modpackDownloadPath)
            .willReturn(ok()
            .withHeader("Content-Type", "application/x-modrinth-modpack+zip")
            .withBody(createModrinthPack(index))));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                wm.getHttpBaseUrl() + modpackDownloadPath, null, null);

        int commandStatus = commandUT.call();

        assertEquals(0, commandStatus);

        String actualFileData =
            new String(Files.readAllBytes(tempDir.resolve(relativeFilePath)));

        assertEquals(expectedFileData, actualFileData);
    }
}
