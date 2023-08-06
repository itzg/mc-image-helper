package me.itzg.helpers.curseforge;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
}