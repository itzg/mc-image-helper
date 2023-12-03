package me.itzg.helpers.modrinth;

import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.DependencyId;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@WireMockTest
public class ModrinthPackInstallerTest {

    @Test
    void installReturnsTheModpackIndexAndInstalledFiles(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        Options fetchOptions = new SharedFetchArgs().options();
        ModpackIndex expectedIndex;
        Installation actualInstallation;
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOptions)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), sharedFetch);

            Path modpackPath = tempDir.resolve("test.mrpack");
            Path resultsFile = tempDir.resolve("results");

            expectedIndex = createBasicModpackIndex();

            Files.write(modpackPath, createModrinthPack(expectedIndex));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOptions, modpackPath, tempDir, resultsFile, false);

            actualInstallation = installerUT.processModpack(sharedFetch).block();
        }

        assertThat(actualInstallation).isNotNull();
        assertThat(actualInstallation.getIndex()).isNotNull();
        assertThat(actualInstallation.getIndex()).isEqualTo(expectedIndex);
        assertThat(actualInstallation.getFiles().size()).isEqualTo(0);
    }

    @Test
    void installDownloadsDependentFilesToInstallation(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        Options fetchOpts = new SharedFetchArgs().options();
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            String expectedFileData = "some test data";
            String relativeFilePath = "test_file";
            Path expectedFilePath = tempDir.resolve(relativeFilePath);
            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            ModpackIndex index = createBasicModpackIndex();
            index.getFiles().add(createHostedModpackFile(
                relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl()));

            Files.write(modpackPath, createModrinthPack(index));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            List<Path> installedFiles = installation.getFiles();

            assertThat(expectedFilePath).isRegularFile();
            assertThat(expectedFilePath).content()
                .isEqualTo(expectedFileData);
            assertThat(installedFiles.size()).isEqualTo(1);
            assertThat(installedFiles.get(0)).isEqualTo(expectedFilePath);
        }
    }

    @Test
    void sanitizesModFilePath(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        Options fetchOpts = new SharedFetchArgs().options();
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            String expectedFileData = "some test data";
            Path expectedFilePath = tempDir.resolve("mods/mod.jar");
            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            ModpackIndex index = createBasicModpackIndex();
            index.getFiles().add(createHostedModpackFile(
                "mods\\mod.jar", "mods/mod.jar", expectedFileData, wm.getHttpBaseUrl()));

            Files.write(modpackPath, createModrinthPack(index));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            List<Path> installedFiles = installation.getFiles();

            assertThat(expectedFilePath).isRegularFile();
            assertThat(expectedFilePath).content()
                .isEqualTo(expectedFileData);
            assertThat(installedFiles.size()).isEqualTo(1);
            assertThat(installedFiles.get(0)).isEqualTo(expectedFilePath);
        }
    }

    @ParameterizedTest
    @MethodSource("handlesExcludedFiles_args")
    void handlesExcludedFiles(String modpackFilePath, String exclude, WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        Options fetchOpts = new SharedFetchArgs().options();
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            final HashMap<Env, EnvType> env = new HashMap<>();
            env.put(Env.client, EnvType.required);
            // some modpack improperly declare server-required
            env.put(Env.server, EnvType.required);

            ModpackIndex index = new ModpackIndex()
                .setName(null)
                .setGame("minecraft")
                .setDependencies(new HashMap<>())
                .setFiles(Collections.singletonList(
                    new ModpackFile()
                        .setPath(modpackFilePath)
                        .setEnv(env)
                ))
                .setVersionId(null);
            index.getDependencies().put(DependencyId.minecraft, "1.20.1");

            Files.write(modpackPath, createModrinthPack(index));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false)
                // Exclude!
                .setExcludeFiles(
                    Collections.singletonList(exclude)
                );

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            assertThat(installation.getFiles()).isEmpty();
        }

    }

    public static Stream<Arguments> handlesExcludedFiles_args() {
        return Stream.of(
            Arguments.arguments("mods/client-mod.jar", "client-mod"),
            Arguments.arguments("mods/ClientMod.jar", "clientmod")
        );
    }

}
