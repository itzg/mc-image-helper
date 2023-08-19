package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionFile;

class ModrinthTestHelpers {
    static final ObjectMapper mapper = new ObjectMapper();

    static Version createModrinthProjectVersion(String versionId) {
        return new Version()
            .setId(versionId)
            .setFiles(new ArrayList<>());
    }

    static void stubModrinthModpackApi(
            WireMockRuntimeInfo wm, String projectName, String projectId,
            Version projectVersion, byte[] expectedData
        ) {
        String modpackDownloadPath = "/download/test_project1.mrpack";

        JsonNode responseProject = mapper.valueToTree(
            new Project()
                .setSlug(projectName)
                .setId(projectId)
                .setTitle("Test"));

        projectVersion.getFiles().add(new VersionFile()
            .setPrimary(true)
            .setUrl(wm.getHttpBaseUrl() + modpackDownloadPath));
        JsonNode responseVersion = mapper.valueToTree(projectVersion);

        List<Version> projectVersionList = new ArrayList<>();
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
        stubFor(get(urlPathMatching("/v2/project/" + projectId + "/version"))
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
            .withBody(expectedData)));
    }

    static ModpackIndex createBasicModpackIndex() {
        ModpackIndex index = new ModpackIndex()
            .setName(null)
            .setGame("minecraft")
            .setDependencies(new HashMap<>())
            .setFiles(new ArrayList<>())
            .setVersionId(null);
        index.getDependencies().put(DependencyId.minecraft, "1.20.1");

        return index;
    }

    static byte[] createModrinthPack(ModpackIndex index) throws IOException {
        ByteArrayOutputStream zipBytesOutputStream =
            new ByteArrayOutputStream();

        ZipOutputStream zipOutputStream =
            new ZipOutputStream(zipBytesOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("modrinth.index.json"));
        zipOutputStream.write(mapper.writeValueAsBytes(index));
        zipOutputStream.closeEntry();
        zipOutputStream.close();

        byte[] zipBytes = zipBytesOutputStream.toByteArray();
        zipBytesOutputStream.close();

        return zipBytes;
    }

    static ModpackIndex.ModpackFile createHostedModpackFile(
            String relativeFileLocation, String data, String wmBaseUrl
        ) throws URISyntaxException
    {
        stubFor(get("/files/" + relativeFileLocation)
            .willReturn(ok().withBody(data)));

        ModpackIndex.ModpackFile modpackFile = new ModpackIndex.ModpackFile()
            .setDownloads(new ArrayList<>())
            .setPath(relativeFileLocation);
        modpackFile.getDownloads().add(
            new URI(wmBaseUrl + "/files/" + relativeFileLocation));

        return modpackFile;
    }
}
