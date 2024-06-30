package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import me.itzg.helpers.errors.ExitCodeMapper;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.modrinth.ModrinthPackInstaller.ModloaderPreparer;
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest
public class InstallModrinthModpackCommandTest {
    private final String projectName = "test_project1";
    private final String projectId = "efgh5678";
    private final String projectVersionId = "abcd1234";
    private final Version projectVersion =
        createModrinthProjectVersion(projectVersionId);

    static InstallModrinthModpackCommand createInstallModrinthModpackCommand(
        String baseUrl, Path outputDir, String projectName, String versionId,
        ModpackLoader loader, ModloaderPreparer mockForgePreparer
    ) {
        final InstallModrinthModpackCommand commandUT =
            new InstallModrinthModpackCommand()
                // so that the modloader prepare can be injected with a mock
                .setInstallerFactory((apiClient, mrPackFile, fileInclusionCalculator) ->
                    new ModrinthPackInstaller(
                        apiClient,
                        SharedFetch.Options.builder().build(),
                        mrPackFile, outputDir, null, false,
                        fileInclusionCalculator
                    )
                        .modifyModLoaderPreparer(DependencyId.forge, mockForgePreparer)
                );
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
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge, mockPreparer
            );

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);

        Mockito.verify(mockPreparer, Mockito.times(1))
            .prepare(Mockito.any(), Mockito.eq(MINECRAFT_VERSION), Mockito.eq("111"));
    }

    @Test
    void downloadsAndInstallsModrinthModpack_versionNumberAndAnyLoader(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, URISyntaxException {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
        index.getFiles().add(testFile);

        String projectVersionNumber = "1.6.1";
        stubModrinthModpackApi(
            wm, projectName, this.projectId,
            createModrinthProjectVersion(projectVersionId).setVersionNumber(projectVersionNumber),
            createModrinthPack(index)
        );

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionNumber, null, mockPreparer
            );

        assertThat(
            commandUT.call()
        ).isEqualTo(0);

        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);

        final ModrinthModpackManifest manifest = Manifests.load(tempDir, ModrinthModpackManifest.ID,
            ModrinthModpackManifest.class
        );

        assertThat(manifest).isNotNull();
        assertThat(manifest.getProjectSlug()).isEqualTo(projectName);
        assertThat(manifest.getVersionId()).isNotNull();

        // attempt to re-install same version
        assertThat(
            commandUT.call()
        ).isEqualTo(0);

        final ModrinthModpackManifest manifestAfterReinstall = Manifests.load(tempDir, ModrinthModpackManifest.ID,
            ModrinthModpackManifest.class
        );

        assertThat(manifestAfterReinstall).isNotNull();
        assertThat(manifestAfterReinstall.getTimestamp())
            .isNotNull()
            .isEqualTo(manifest.getTimestamp());

        Mockito.verify(mockPreparer, Mockito.times(1))
            .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
    }

    @Test
    void createsModrinthModpackManifestForModpackInstallation(
                WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException {
        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge, mockPreparer
            );

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);

        ModrinthModpackManifest installedManifest = Manifests.load(tempDir,
            ModrinthModpackManifest.ID, ModrinthModpackManifest.class);

        assertThat(installedManifest).isNotNull();
        assertThat(installedManifest.getProjectSlug())
            .isEqualTo(projectName);
        assertThat(installedManifest.getVersionId())
            .isEqualTo(projectVersionId);
        assertThat(installedManifest.getFiles().size())
            .isEqualTo(0);
        assertThat(installedManifest.getDependencies())
            .isEqualTo(index.getDependencies());

        Mockito.verify(mockPreparer, Mockito.times(1))
            .prepare(Mockito.any(), Mockito.eq(MINECRAFT_VERSION), Mockito.eq("111"));
    }

    @Test
    void removesFilesNoLongerNeedeByUpdatedModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
            projectName, projectVersionId, ModpackLoader.forge, mockPreparer
        )
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
                projectName, newProjectVersionId, ModpackLoader.forge, mockPreparer
            )
                .call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).doesNotExist();

        Mockito.verify(mockPreparer, Mockito.times(2))
            .prepare(Mockito.any(), Mockito.eq(MINECRAFT_VERSION), Mockito.eq("111"));
    }

    @Test
    void downloadsAndInstallsGenericModpacksOverHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        String modpackDownloadPath = "/files/modpacks/test_modpack-1.0.0.mrpack";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
        index.getFiles().add(testFile);

        stubFor(get(modpackDownloadPath)
            .willReturn(ok()
            .withHeader("Content-Type", "application/x-modrinth-modpack+zip")
            .withBody(createModrinthPack(index))));

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                wm.getHttpBaseUrl() + modpackDownloadPath, null, null, mockPreparer
            );

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);

        Mockito.verify(mockPreparer, Mockito.times(1))
            .prepare(Mockito.any(), Mockito.eq(MINECRAFT_VERSION), Mockito.eq("111"));
    }

    @Test
    void usesLocalModpackFile(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, URISyntaxException {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
        index.getFiles().add(testFile);

        final Path localMrpackFile =
            Files.write(tempDir.resolve("slug.mrpack"), createModrinthPack(index));

        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        final String projectRef = localMrpackFile.toString();
        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectRef, null, null,
                mockPreparer
            );

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);

        Mockito.verify(mockPreparer, Mockito.times(1))
            .prepare(Mockito.any(), Mockito.eq(MINECRAFT_VERSION), Mockito.eq("111"));
    }

    @Test
    void errorWhenNoCompatibleVersions(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        final ObjectMapper mapper = new ObjectMapper();

        JsonNode responseProject = mapper.valueToTree(
            new Project()
                .setSlug(projectName)
                .setId(projectId)
                .setTitle("Test"));

        stubFor(get("/v2/project/" + projectName)
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(responseProject)));

        JsonNode responseVersionList = mapper.valueToTree(Collections.singletonList(
            new Version()
                .setId(RandomStringUtils.randomAlphabetic(5))
                // type is beta, but default requested is release-only
                .setVersionType(VersionType.beta)
        ));

        stubFor(get(urlPathMatching("/v2/project/" + projectId + "/version"))
            .withQueryParam("game_versions", equalTo("[\"1.20.2\"]"))
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(responseVersionList)));

        final int exitCode = new CommandLine(new InstallModrinthModpackCommand())
            .setExitCodeExceptionMapper(new ExitCodeMapper())
            .execute(
                "--api-base-url", wm.getHttpBaseUrl(),
                "--project", projectName,
                "--game-version", "1.20.2",
                "--output-directory", tempDir.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.USAGE);
    }
}
