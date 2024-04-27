package me.itzg.helpers.curseforge;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.FileIndex;
import me.itzg.helpers.curseforge.model.ModsSearchResponse;
import me.itzg.helpers.files.Manifests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CurseForgeFilesCommandTest {

    @TempDir
    Path tempDir;

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("curseforge")
            .extensions(new ResponseTemplateTransformer(false))
        )
        .configureStaticDsl(true)
        .build();

    @Test
    void oneOfEachCategoryAndUpgrade() {
        int exitCode = new CommandLine(
            new CurseForgeFilesCommand()
        )
            .execute(
                "--api-base-url", wm.baseUrl(),
                "--api-key", "test",
                "--output-directory", tempDir.toString(),
                "--default-category", "mc-mods",
                "jei:4434385",
                "https://www.curseforge.com/minecraft/bukkit-plugins/worldguard/files/3677516"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve("mods/jei-1.18.2-forge-10.2.1.1003.jar")).exists();
        assertThat(tempDir.resolve("plugins/worldguard-bukkit-7.0.7-dist.jar")).exists();

        // upgrade jei
        exitCode = new CommandLine(
            new CurseForgeFilesCommand()
        )
            .execute(
                "--api-base-url", wm.baseUrl(),
                "--api-key", "test",
                "--output-directory", tempDir.toString(),
                "--default-category", "mc-mods",
                "jei:4593548",
                "https://www.curseforge.com/minecraft/bukkit-plugins/worldguard/files/3677516"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve("mods/jei-1.18.2-forge-10.2.1.1003.jar")).doesNotExist();
        assertThat(tempDir.resolve("mods/jei-1.18.2-forge-10.2.1.1005.jar")).exists();
        assertThat(tempDir.resolve("plugins/worldguard-bukkit-7.0.7-dist.jar")).exists();

        // ...and remove all
        exitCode = new CommandLine(
            new CurseForgeFilesCommand()
        )
            .execute(
                "--api-base-url", wm.baseUrl(),
                "--api-key", "test",
                "--output-directory", tempDir.toString(),
                "--default-category", "mc-mods"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve("mods/jei-1.18.2-forge-10.2.1.1005.jar")).doesNotExist();
        assertThat(tempDir.resolve("plugins/worldguard-bukkit-7.0.7-dist.jar")).doesNotExist();
    }

    @Test
    void usingListingFile() throws IOException {
        final Path listingFile = Files.write(tempDir.resolve("listing.txt"),
            Arrays.asList("# Comment", "jei:4434385", "", "https://www.curseforge.com/minecraft/bukkit-plugins/worldguard/files/3677516")
            );

        int exitCode = new CommandLine(
            new CurseForgeFilesCommand()
        )
            .execute(
                "--api-base-url", wm.baseUrl(),
                "--api-key", "test",
                "--output-directory", tempDir.toString(),
                "--default-category", "mc-mods",
                "@" + listingFile
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve("mods/jei-1.18.2-forge-10.2.1.1003.jar")).exists();
        assertThat(tempDir.resolve("plugins/worldguard-bukkit-7.0.7-dist.jar")).exists();

    }

    @Test
    void handlesDuplicateManifestEntries() throws IOException {
        final Path manifestFile = Files.write(tempDir.resolve(Manifests.buildManifestPath(tempDir, CurseForgeFilesManifest.ID)), ("{\n"
            + "  \"@type\": \"me.itzg.helpers.curseforge.CurseForgeFilesManifest\",\n"
            + "  \"timestamp\": \"2024-04-27T17:48:56.529386300Z\",\n"
            + "  \"files\": null,\n"
            + "  \"entries\": [\n"
            + "    {\n"
            + "      \"ids\": {\n"
            + "        \"modId\": 667389,\n"
            + "        \"fileId\": 4802504\n"
            + "      },\n"
            + "      \"filePath\": \"mods\\\\goblintraders-fabric-1.20.1-1.9.3.jar\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"ids\": {\n"
            + "        \"modId\": 667389,\n"
            + "        \"fileId\": 4802504\n"
            + "      },\n"
            + "      \"filePath\": \"mods\\\\goblintraders-fabric-1.20.1-1.9.3.jar\"\n"
            + "    }\n"
            + "  ]\n"
            + "}").getBytes(StandardCharsets.UTF_8));

        wm.stubFor(get(urlPathEqualTo("/v1/mods/search"))
            .withQueryParam("gameId", WireMock.equalTo("432"))
            .withQueryParam("slug", WireMock.equalTo("goblintraders-fabric"))
            .withQueryParam("classId", WireMock.equalTo("6"))
            .willReturn(
                jsonResponse(new ModsSearchResponse()
                    .setData(singletonList(
                        new CurseForgeMod()
                            .setId(667389)
                            .setClassId(6)
                            .setGameId(432)
                            .setLatestFilesIndexes(singletonList(
                                new FileIndex()
                                    .setGameVersion("1.20.1")
                                    .setFileId(4802504)
                            ))
                            .setLatestFiles(singletonList(
                                new CurseForgeFile()
                                    .setId(4802504)
                                    .setModId(667389)
                                    .setDependencies(Collections.emptyList())
                                    // reference cdn.json declaration
                                    .setDownloadUrl(wm.baseUrl() + "/files/goblintraders-fabric-1.20.1-1.9.3.jar")
                                    .setFileName("goblintraders-fabric-1.20.1-1.9.3.jar")
                            ))
                    )), 200)
            )
        );

        int exitCode = new CommandLine(
            new CurseForgeFilesCommand()
        )
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(
                "--api-base-url", wm.baseUrl(),
                "--api-key", "test",
                "--output-directory", tempDir.toString(),
                "--default-category", "mc-mods",
                "--game-version=1.20.1",
                "--mod-loader=fabric",
                "https://www.curseforge.com/minecraft/mc-mods/goblintraders-fabric",
                "https://www.curseforge.com/minecraft/mc-mods/goblintraders-fabric"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(manifestFile)
            .exists();

        final CurseForgeFilesManifest manifest = Manifests.load(tempDir, CurseForgeFilesManifest.ID, CurseForgeFilesManifest.class);
        assertThat(manifest).isNotNull();
        assertThat(manifest.getEntries()).hasSize(1);
        assertThat(manifest.getEntries().get(0).getIds()).isEqualTo(new ModFileIds(667389, 4802504));
    }
}