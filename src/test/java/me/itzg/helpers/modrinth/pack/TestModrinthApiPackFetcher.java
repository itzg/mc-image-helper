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

    @Test
    void testApiFetcherFetchesModpackBySlugAndVersion(WireMockRuntimeInfo wm) 
        throws JsonProcessingException, IOException {

        String projectName = "test_project1";
        String projectId = "efgh5678";
        String projectVersionId = "abcd1234";
        String modpackDownloadPath = "/download/test_project1.mrpack";
        String expectedModpackData = "test_data";
        // ModrinthPack testPack = new ModrinthPack(null);
        // ModrinthPack.Config config = testPack.new Config().setApiBaseUrl(wm.getHttpBaseUrl()).setProject(projectName).setVersion(projectVersion);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseProject = mapper.valueToTree(
            new Project()
                .setSlug(projectName)
                .setId(projectId)
                .setTitle("Test"));

        Version projectVersion = new Version()
            .setId(projectVersionId)
            .setFiles(new ArrayList<VersionFile>());
        projectVersion.getFiles().add(new VersionFile()
            .setPrimary(true)
            .setUrl(wm.getHttpBaseUrl() + modpackDownloadPath));
        JsonNode responseVersion = mapper.valueToTree(projectVersion);

        stubFor(get("/v2/project/" + projectName)
            .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withJsonBody(responseProject)));
        stubFor(get("/v2/version/" + projectVersionId)
            .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withJsonBody(responseVersion)));
        stubFor(get(modpackDownloadPath)
            .willReturn(ok()
            .withHeader("Content-Type", "application/x-modrinth-modpack+zip")
            .withBody(expectedModpackData)));

        ModrinthPack.Config config = new ModrinthPack.Config()
            .setApiBaseUrl(wm.getHttpBaseUrl())
            .setSharedFetchArgs(new SharedFetchArgs())
            .setProject(projectName)
            .setVersion(projectVersionId)
            .setLoader(ModpackLoader.forge);
        
        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(config);
        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        
        assertEquals(expectedModpackData, new String(Files.readAllBytes(mrpackFile)));
    }
}
