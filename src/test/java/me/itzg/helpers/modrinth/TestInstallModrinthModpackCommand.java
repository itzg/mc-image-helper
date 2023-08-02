package me.itzg.helpers.modrinth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestInstallModrinthModpackCommand {
    @Test
    void downloadsAndInstallsModrinthModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException, URISyntaxException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        Version projectVersion = createModrinthProjectVersion(projectVersionId);

        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        Path expectedFilePath = tempDir.resolve(relativeFilePath);

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl()));
        byte[] modpackBytes = createModrinthPack(index);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, modpackBytes);

        InstallModrinthModpackCommand commandUT = new InstallModrinthModpackCommand();
        commandUT.baseUrl = wm.getHttpBaseUrl();
        commandUT.outputDirectory = tempDir;
        commandUT.modpackProject = projectName;
        commandUT.version = projectVersionId;
        commandUT.loader = ModpackLoader.forge;

        int status = commandUT.call();

        assertEquals(0, status);
        assertTrue(Files.exists(expectedFilePath));

        String actualFileData =
            new String(Files.readAllBytes(expectedFilePath));

        assertEquals(expectedFileData, actualFileData);
    }
}
