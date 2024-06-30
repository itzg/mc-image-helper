package me.itzg.helpers.modrinth;

import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.ModrinthPackInstaller.ModloaderPreparer;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

@WireMockTest
public class ModrinthPackInstallerTest {

    @Test
    void installReturnsTheModpackIndexAndInstalledFiles(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        Options fetchOptions = new SharedFetchArgs().options();
        ModpackIndex expectedIndex;
        Installation actualInstallation;
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOptions)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), sharedFetch);

            Path modpackPath = tempDir.resolve("test.mrpack");
            Path resultsFile = tempDir.resolve("results");

            expectedIndex = createBasicModpackIndex(DependencyId.forge, "111");

            Files.write(modpackPath, createModrinthPack(expectedIndex));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOptions, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            actualInstallation = installerUT.processModpack(sharedFetch).block();
        }

        assertThat(actualInstallation).isNotNull();
        assertThat(actualInstallation.getIndex()).isNotNull();
        assertThat(actualInstallation.getIndex()).isEqualTo(expectedIndex);
        assertThat(actualInstallation.getFiles().size()).isEqualTo(0);

        verify(mockPreparer)
            .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
    }

    public static Stream<Arguments> usesSpecificModLoader_args() {
        return Stream.of(
            Arguments.arguments(DependencyId.forge, "111"),
            Arguments.arguments(DependencyId.neoforge, "222"),
            Arguments.arguments(DependencyId.fabricLoader, "333"),
            Arguments.arguments(DependencyId.quiltLoader, "444")
        );
    }

    @ParameterizedTest
    @MethodSource("usesSpecificModLoader_args")
    void usesSpecificModLoader(
            DependencyId modLoaderId, String modLoaderVersion,
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        Options fetchOptions = new SharedFetchArgs().options();
        ModpackIndex expectedIndex;
        Installation actualInstallation;
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOptions)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), sharedFetch);

            Path modpackPath = tempDir.resolve("test.mrpack");
            Path resultsFile = tempDir.resolve("results");

            expectedIndex = createBasicModpackIndex(
                modLoaderId, modLoaderVersion);

            Files.write(modpackPath, createModrinthPack(expectedIndex));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOptions, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .modifyModLoaderPreparer(modLoaderId, mockPreparer);

            actualInstallation = installerUT.processModpack(sharedFetch).block();
        }

        assertThat(actualInstallation).isNotNull();
        assertThat(actualInstallation.getIndex()).isNotNull();
        assertThat(actualInstallation.getIndex()).isEqualTo(expectedIndex);
        assertThat(actualInstallation.getFiles().size()).isEqualTo(0);

        verify(mockPreparer)
            .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq(modLoaderVersion));
    }

    @Test
    void installDownloadsDependentFilesToInstallation(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        Options fetchOpts = new SharedFetchArgs().options();
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            String expectedFileData = "some test data";
            String relativeFilePath = "test_file";
            Path expectedFilePath = tempDir.resolve(relativeFilePath);
            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
            index.getFiles().add(createHostedModpackFile(
                relativeFilePath, relativeFilePath, expectedFileData, wm.getHttpBaseUrl()));

            Files.write(modpackPath, createModrinthPack(index));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            List<Path> installedFiles = installation.getFiles();

            assertThat(expectedFilePath).isRegularFile();
            assertThat(expectedFilePath).content()
                .isEqualTo(expectedFileData);
            assertThat(installedFiles.size()).isEqualTo(1);
            assertThat(installedFiles.get(0)).isEqualTo(expectedFilePath);

            verify(mockPreparer)
                .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
        }
    }

    @Test
    void sanitizesModFilePath(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);

        Options fetchOpts = new SharedFetchArgs().options();
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            String expectedFileData = "some test data";
            Path expectedFilePath = tempDir.resolve("mods/mod.jar");
            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            ModpackIndex index = createBasicModpackIndex(DependencyId.forge, "111");
            index.getFiles().add(createHostedModpackFile(
                "mods\\mod.jar", "mods/mod.jar", expectedFileData, wm.getHttpBaseUrl()));

            Files.write(modpackPath, createModrinthPack(index));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            List<Path> installedFiles = installation.getFiles();

            assertThat(expectedFilePath).isRegularFile();
            assertThat(expectedFilePath).content()
                .isEqualTo(expectedFileData);
            assertThat(installedFiles.size()).isEqualTo(1);
            assertThat(installedFiles.get(0)).isEqualTo(expectedFilePath);

            verify(mockPreparer)
                .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
        }
    }

    @ParameterizedTest
    @MethodSource("handlesExcludedFiles_args")
    void handlesExcludedFiles(String modpackFilePath, String exclude, boolean usingExcludeIncludeFile,
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);
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
            index.getDependencies().put(DependencyId.forge, "111");

            Files.write(modpackPath, createModrinthPack(index));

            final FileInclusionCalculator fileInclusionCalculator;
            if (usingExcludeIncludeFile) {
                fileInclusionCalculator = new FileInclusionCalculator(null, null, null,
                    new ExcludeIncludesContent()
                        .setGlobalExcludes(new HashSet<>(Collections.singletonList(exclude)))
                );
            }
            else {
                fileInclusionCalculator = new FileInclusionCalculator(null,
                    // Exclude!
                    Collections.singletonList(exclude),
                    null, null
                );
            }

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false,
                fileInclusionCalculator
            )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            assertThat(installation.getFiles()).isEmpty();

            verify(mockPreparer)
                .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
        }

    }

    @Test
    void handlesOverrides(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);
        Options fetchOpts = new SharedFetchArgs().options();
        ModpackIndex expectedIndex;

        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            expectedIndex = createBasicModpackIndex(DependencyId.forge, "111");

            final Path src = tempDir.resolve("src");
            final Path extraDir =
                Files.createDirectories(src.resolve("extra"));
            final Path fileToExclude = Files.write(extraDir.resolve("file.txt"), Collections.singletonList("line1"));

            Files.write(modpackPath, createModrinthPack(expectedIndex, "overrides", src, fileToExclude));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            assertThat(installation.getIndex()).isEqualTo(expectedIndex);
            assertThat(installation.getFiles()).hasSize(1);
            assertThat(installation.getFiles().get(0))
                .isEqualTo(tempDir.resolve("extra").resolve("file.txt"));

            verify(mockPreparer)
                .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
        }

    }

    @ParameterizedTest
    @ValueSource(strings = {
        "extra/file.txt",
        "extra/*.txt",
        "**/*.txt",
        "extra/**"
    })
    void handlesOverrideExcludedFiles(String exclusion, WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final ModloaderPreparer mockPreparer = Mockito.mock(ModloaderPreparer.class);
        Options fetchOpts = new SharedFetchArgs().options();
        ModpackIndex expectedIndex;

        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", fetchOpts)) {
            ModrinthApiClient apiClient = new ModrinthApiClient(
                wm.getHttpBaseUrl(), "install-modrinth-modpack", fetchOpts);

            Path resultsFile = tempDir.resolve("results");
            Path modpackPath = tempDir.resolve("test.mrpack");

            expectedIndex = createBasicModpackIndex(DependencyId.forge, "111");

            final Path extraDir = Files.createDirectory(tempDir.resolve("extra"));
            final Path fileToExclude = Files.write(extraDir.resolve("file.txt"), Collections.singletonList("line1"));

            Files.write(modpackPath, createModrinthPack(expectedIndex, "overrides", tempDir, fileToExclude));

            ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
                apiClient, fetchOpts, modpackPath, tempDir, resultsFile, false,
                FileInclusionCalculator.empty()
            )
                .setOverridesExclusions(
                    Collections.singletonList(exclusion)
                )
                .modifyModLoaderPreparer(DependencyId.forge, mockPreparer);

            final Installation installation = installerUT.processModpack(sharedFetch).block();

            assertThat(installation).isNotNull();
            assertThat(installation.getIndex()).isEqualTo(expectedIndex);
            assertThat(installation.getFiles()).isEmpty();

            verify(mockPreparer)
                .prepare(Mockito.any(), Mockito.eq("1.20.1"), Mockito.eq("111"));
        }

    }

    public static Stream<Arguments> handlesExcludedFiles_args() {
        return Stream.of(
            Arguments.arguments("mods/client-mod.jar", "client-mod", false),
            Arguments.arguments("mods/ClientMod.jar", "clientmod", false),
            Arguments.arguments("mods/client-mod.jar", "client-mod", true),
            Arguments.arguments("mods/ClientMod.jar", "clientmod", true)
        );
    }

}
