package me.itzg.helpers.modrinth;

import static me.itzg.helpers.modrinth.ModrinthTestHelpers.createModrinthProjectVersion;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.stubModrinthModpackApi;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public class ModrinthApiPackFetcherTest {
    @Test
    void testApiFetcherFetchesModpackBySlugAndVersionId(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) {
        String projectName = "test_project1";
        String projectId = randomAlphanumeric(8);
        String projectVersionId = randomAlphanumeric(8);
        byte[] expectedModpackData = "test_data".getBytes();
        Version projectVersion = createModrinthProjectVersion(projectVersionId);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, projectVersionId);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, false, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader());

        final FetchedPack fetchedPack = fetcherUT.fetchModpack(null).block();
        assertThat(fetchedPack).isNotNull();
        assertThat(fetchedPack.getMrPackFile()).content()
            .isEqualTo(new String(expectedModpackData));
        assertThat(fetchedPack.getProjectSlug()).isEqualTo(projectName);
        assertThat(fetchedPack.getVersionId()).isEqualTo(projectVersionId);
    }

    @Test
    void testIgnoresMissingFile(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException {
        String projectName = "test_project1";
        String projectId = randomAlphanumeric(8);
        String projectVersionId = randomAlphanumeric(8);
        byte[] expectedModpackData = "test_data".getBytes();
        Version projectVersion = createModrinthProjectVersion(projectVersionId);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, projectVersionId);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, false, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader()
        )
            .setIgnoreMissingFiles(Collections.singletonList("config/temp.txt"));

        final Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
        Files.createFile(modsDir.resolve("something.jar"));
        Files.createDirectories(tempDir.resolve("config"));

        final ModrinthModpackManifest prevManifest = ModrinthModpackManifest.builder()
            .projectSlug(projectName)
            .versionId(projectVersionId)
            .files(Arrays.asList(
                "mods/something.jar",
                "config/temp.txt"
            ))
            .build();
        final FetchedPack fetchedPack = fetcherUT.fetchModpack(prevManifest).block();
        // will be null/empty when up-to-date
        assertThat(fetchedPack).isNull();
    }

    @Test
    void testApiFetcherFetchesLatestModpackWhenVersionTypeSpecified(
            WireMockRuntimeInfo wm,  @TempDir Path tempDir
        ) {
        String projectName = "test_project1";
        String projectId = randomAlphanumeric(8);
        String projectVersionId = randomAlphanumeric(8);
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
            apiClient, testProjectRef, false, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader()
        );

        final FetchedPack fetchedPack = fetcherUT.fetchModpack(null).block();
        assertThat(fetchedPack).isNotNull();
        assertThat(fetchedPack.getMrPackFile()).content()
            .isEqualTo(new String(expectedModpackData));

        assertThat(fetchedPack.getProjectSlug()).isEqualTo(projectName);
        assertThat(fetchedPack.getVersionId()).isEqualTo(projectVersionId);
    }

    @Test
    void testApiFetcherFetchesNumberedVersions(
            WireMockRuntimeInfo wm,  @TempDir Path tempDir
        ) {
        String projectName = "test_project1";
        String projectId = randomAlphanumeric(8);
        String projectVersionNumber = "1.0.0";
        byte[] expectedModpackData = "test_data".getBytes();
        final String projectVersionId = randomAlphanumeric(8);
        Version projectVersion = createModrinthProjectVersion(projectVersionId)
            .setVersionType(VersionType.release)
            .setVersionNumber(projectVersionNumber);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion, expectedModpackData);

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            new SharedFetchArgs().options());
        ProjectRef testProjectRef = new ProjectRef(projectName, projectVersionNumber);

        ModrinthApiPackFetcher fetcherUT = new ModrinthApiPackFetcher(
            apiClient, testProjectRef, false, tempDir, "",
            VersionType.release, ModpackLoader.forge.asLoader());

        final FetchedPack fetchedPack = fetcherUT.fetchModpack(null).block();
        assertThat(fetchedPack).isNotNull();
        assertThat(fetchedPack.getMrPackFile()).content()
            .isEqualTo(new String(expectedModpackData));

        assertThat(fetchedPack.getProjectSlug()).isEqualTo(projectName);
        assertThat(fetchedPack.getVersionId()).isEqualTo(projectVersionId);
    }
}
