package me.itzg.helpers.modrinth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import me.itzg.helpers.modrinth.model.*;

class ModrinthTestHelpers {
    static final ObjectMapper mapper = new ObjectMapper();

    static Version createModrinthProjectVersion(String versionId) {
        return new Version()
            .setId(versionId)
            .setFiles(new ArrayList<VersionFile>());
    }

    static void stubModrinthModpackApi(
            WireMockRuntimeInfo wm, String projectName, String projectId,
            Version projectVersion, String expectedData
        ) throws JsonProcessingException, IOException
    {
        String modpackDownloadPath = "/download/test_project1.mrpack";
        String expectedModpackData = "test_data";

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

    static ModpackIndex createBasicModpackIndex() {
        ModpackIndex index = new ModpackIndex()
            .setName(null)
            .setGame("minecraft")
            .setDependencies(new HashMap<DependencyId, String>())
            .setFiles(new ArrayList<ModpackIndex.ModpackFile>())
            .setVersionId(null);
        index.getDependencies().put(DependencyId.minecraft, "1.20.1");

        return index;
    }

    static Path createModrinthPack(ModpackIndex index, Path basePath)
            throws IOException
    {
        ByteArrayOutputStream zipBytesOutputStream =
            new ByteArrayOutputStream();

        ZipOutputStream zipOutputStream =
            new ZipOutputStream(zipBytesOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("modrinth.index.json"));
        zipOutputStream.write(mapper.writeValueAsBytes(index));
        zipOutputStream.closeEntry();
        zipOutputStream.close();

        Path modpackPath = basePath.resolve("testpack.mrpack");
        OutputStream modpackFileOutputStream =
            Files.newOutputStream(modpackPath);
        modpackFileOutputStream.write(zipBytesOutputStream.toByteArray());
        modpackFileOutputStream.close();
        zipBytesOutputStream.close();

        return modpackPath;
    }

}
