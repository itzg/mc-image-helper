package me.itzg.helpers.modrinth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;

import java.io.IOException;
import java.nio.file.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestModrinthApiPackFetcher {
    @Test
    void testApiFetcherFetchesModpackBySlugAndVersionId(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        byte[] expectedModpackData = "test_data".getBytes();
        Version projectVersion = createModrinthProjectVersion(projectVersionId);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, projectVersionId);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader());
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(new String(expectedModpackData), actualModpackData);
    }

    @Test
    void testApiFetcherFetchesLatestModpackWhenVersionTypeSpecified(
            WireMockRuntimeInfo wm,  @TempDir Path tempDir
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        byte[] expectedModpackData = "test_data".getBytes();
        Version projectVersion = createModrinthProjectVersion(projectVersionId)
            .setVersionType(VersionType.release);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        InstallModrinthModpackCommand config = new InstallModrinthModpackCommand();
        config.baseUrl = wm.getHttpBaseUrl();
        config.sharedFetchArgs = new SharedFetchArgs();
        config.modpackProject = projectName;
        config.version = "release";
        config.loader = ModpackLoader.forge;

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, "release");

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader());

        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(new String(expectedModpackData), actualModpackData);
    }

    @Test
    void testApiFetcherFetchesNumberedVersions(
            WireMockRuntimeInfo wm,  @TempDir Path tempDir
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionNumber = "1.0.0";
        byte[] expectedModpackData = "test_data".getBytes();
        Version projectVersion = createModrinthProjectVersion("abcd1234")
            .setVersionType(VersionType.release)
            .setVersionNumber(projectVersionNumber);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, projectVersionNumber);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader());
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(new String(expectedModpackData), actualModpackData);
    }
}
