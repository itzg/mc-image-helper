package me.itzg.helpers.vanillatweaks;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class VanillaTweaksCommandTest {

    @Test
    void testOneOfEachSharecode(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) {
        wmInfo.getWireMock().loadMappingsFrom("src/test/resources/vanillatweaks");

        // https://vanillatweaks.net/share#4mwbtQ resource pack
        // https://vanillatweaks.net/share#rG4JRm data pack
        // https://vanillatweaks.net/share#sgMq8u crafting tweaks
        final int exitCode = new CommandLine(
            new VanillaTweaksCommand()
        )
            .execute(
                "--base-url", wmInfo.getHttpBaseUrl(),
                "--share-codes=4mwbtQ,rG4JRm,sgMq8u",
                "--output-directory", tempDir.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(tempDir.resolve("resourcepacks").resolve("VanillaTweaks_98060d2.zip"))
            .hasContent("4mwbtQ");

        final Path datapacksDir = tempDir.resolve("world").resolve("datapacks");
        assertThat(datapacksDir.resolve("datapack.txt"))
            .hasContent("rG4JRm");
        assertThat(datapacksDir.resolve("VanillaTweaks_978e11f.zip"))
            .hasContent("sgMq8u");
    }
}