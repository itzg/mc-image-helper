package me.itzg.helpers.modrinth.pack;

import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.ModpackLoader;
import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestModrinthApiPackFetcher {

    private Version buildVersion(String versionId) {
        return new Version()
            .setId(versionId)
            .setFiles(new ArrayList<VersionFile>());
    }

    private void stubModrinthModpack(
            WireMockRuntimeInfo wm, String projectName, String projectId,
            Version projectVersion, String expectedData
        ) throws JsonProcessingException, IOException
    {
        String modpackDownloadPath = "/download/test_project1.mrpack";
        String expectedModpackData = "test_data";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseProject = mapper.valueToTree(
            new Project()
                .setSlug(projectName)
                .setId(projectId)
                .setTitle("Test"));

        projectVersion.getFiles().add(new VersionFile()
            .setPrimary(true)
            .setUrl(wm.getHttpBaseUrl() + modpackDownloadPath));
        JsonNode responseVersion = mapper.valueToTree(projectVersion);

        List<Version> projectVersionList = new ArrayList<Version>();
        projectVersionList.add(projectVersion);
        JsonNode responseVersionList = mapper.valueToTree(projectVersionList);

        stubFor(get("/v2/project/" + projectName)
            .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withJsonBody(responseProject)));
        stubFor(get(urlPathMatching("/v2/project/" + projectId + "/version"))
            .withQueryParam("loader", equalTo("[\"forge\"]"))
            .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withJsonBody(responseVersionList)));
        stubFor(get("/v2/version/" + projectVersion.getId())
            .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withJsonBody(responseVersion)));
        stubFor(get(modpackDownloadPath)
            .willReturn(ok()
            .withHeader("Content-Type", "application/x-modrinth-modpack+zip")
            .withBody(expectedModpackData)));
    }

    @Test
    void testApiFetcherFetchesModpackBySlugAndVersionId(
            WireMockRuntimeInfo wm
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        String expectedModpackData = "test_data";
        Version projectVersion = buildVersion(projectVersionId);

        stubModrinthModpack(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthPack.Config config = new ModrinthPack.Config()
            .setApiBaseUrl(wm.getHttpBaseUrl())
            .setSharedFetchArgs(new SharedFetchArgs())
            .setProject(projectName)
            .setVersion(projectVersionId)
            .setLoader(ModpackLoader.forge);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(config);
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(expectedModpackData, actualModpackData);
    }

    @Test
    void testApiFetcherFetchesLatestModpackWhenVersionTypeSpecified(
            WireMockRuntimeInfo wm
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        String expectedModpackData = "test_data";
        Version projectVersion = buildVersion(projectVersionId)
            .setVersionType(VersionType.release);

        stubModrinthModpack(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthPack.Config config = new ModrinthPack.Config()
            .setApiBaseUrl(wm.getHttpBaseUrl())
            .setSharedFetchArgs(new SharedFetchArgs())
            .setProject(projectName)
            .setVersion("release")
            .setLoader(ModpackLoader.forge);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(config);
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(expectedModpackData, actualModpackData);
    }

    @Test
    void testApiFetcherFetchesNumberedVersions(
            WireMockRuntimeInfo wm
        ) throws JsonProcessingException, IOException
    {
        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionNumber = "1.0.0";
        String expectedModpackData = "test_data";
        Version projectVersion = buildVersion("abcd1234")
            .setVersionType(VersionType.release)
            .setVersionNumber(projectVersionNumber);

        stubModrinthModpack(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthPack.Config config = new ModrinthPack.Config()
            .setApiBaseUrl(wm.getHttpBaseUrl())
            .setSharedFetchArgs(new SharedFetchArgs())
            .setProject(projectName)
            .setVersion(projectVersionNumber)
            .setLoader(ModpackLoader.forge);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(config);
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(expectedModpackData, actualModpackData);
    }
}
