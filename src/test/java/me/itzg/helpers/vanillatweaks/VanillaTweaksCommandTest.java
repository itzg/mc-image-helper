package me.itzg.helpers.vanillatweaks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest
class VanillaTweaksCommandTest {

    private static final String DATAPACKS_ZIP_PATH = "/download/VanillaTweaks_d1234567_UNZIP_ME.zip";

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
        assertThat(datapacksDir.resolve("armor statues v2.8.7 (MC 1.19).zip"))
            .hasContent("rG4JRm");
        assertThat(datapacksDir.resolve("VanillaTweaks_978e11f.zip"))
            .hasContent("sgMq8u");
    }

    @Test
    void failsWhenRequestedDatapackIsMissingFromZip(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) throws IOException {
        final Path previouslyInstalled = givenPreviouslyInstalledDatapack(tempDir);
        stubDatapacksZipLink();
        stubFor(get(DATAPACKS_ZIP_PATH)
            .willReturn(ok().withBody(zipOf("timber v2.1.18 (MC 1.21-1.21.11).zip")))
        );

        final LatchingExecutionExceptionHandler handler = new LatchingExecutionExceptionHandler();
        final int exitCode = new CommandLine(new VanillaTweaksCommand())
            .setExecutionExceptionHandler(handler)
            .execute(
                "--base-url", wmInfo.getHttpBaseUrl(),
                "--share-codes=",
                "--pack-files", writePackFile(tempDir, "timber", "no such pack").toString(),
                "--output-directory", tempDir.toString()
            );

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(handler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("[no such pack]");
        assertThat(Files.list(previouslyInstalled.getParent())).containsExactly(previouslyInstalled);
    }

    @Test
    void failsWhenZipLinkStatusIsError(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) throws IOException {
        stubFor(post("/assets/server/zipdatapacks.php")
            .willReturn(okJson("{\"status\":\"error\",\"message\":\"There was an error. Packs were invalid.\"}"))
        );

        final LatchingExecutionExceptionHandler handler = new LatchingExecutionExceptionHandler();
        final int exitCode = new CommandLine(new VanillaTweaksCommand())
            .setExecutionExceptionHandler(handler)
            .execute(
                "--base-url", wmInfo.getHttpBaseUrl(),
                "--share-codes=",
                "--pack-files", writePackFile(tempDir, "timber").toString(),
                "--output-directory", tempDir.toString()
            );

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(handler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("Packs were invalid");
    }

    @Test
    void failsWhenDatapacksZipIsTruncated(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) throws IOException {
        final Path previouslyInstalled = givenPreviouslyInstalledDatapack(tempDir);
        final byte[] zip = zipOf("timber v2.1.18 (MC 1.21-1.21.11).zip", "elevators v1.0.16 (MC 1.21-1.21.11).zip");
        stubDatapacksZipLink();
        stubFor(get(DATAPACKS_ZIP_PATH)
            // drop the central directory, as if the connection closed early
            .willReturn(aResponse().withStatus(200).withBody(Arrays.copyOf(zip, zip.length / 2)))
        );

        final LatchingExecutionExceptionHandler handler = new LatchingExecutionExceptionHandler();
        final int exitCode = new CommandLine(new VanillaTweaksCommand())
            .setExecutionExceptionHandler(handler)
            .execute(
                "--base-url", wmInfo.getHttpBaseUrl(),
                "--share-codes=",
                "--pack-files", writePackFile(tempDir, "timber", "elevators").toString(),
                "--output-directory", tempDir.toString()
            );

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        assertThat(handler.getExecutionException()).hasRootCauseInstanceOf(ZipException.class);
        assertThat(Files.list(previouslyInstalled.getParent())).containsExactly(previouslyInstalled);
    }

    private static Path givenPreviouslyInstalledDatapack(Path tempDir) throws IOException {
        final Path datapacksDir = Files.createDirectories(tempDir.resolve("world").resolve("datapacks"));
        return Files.writeString(datapacksDir.resolve("timber v2.1.17 (MC 1.21-1.21.11).zip"), "previous");
    }

    private static void stubDatapacksZipLink() {
        stubFor(post("/assets/server/zipdatapacks.php")
            .willReturn(okJson("{\"status\":\"success\",\"link\":\"" + DATAPACKS_ZIP_PATH + "\"}"))
        );
    }

    private static Path writePackFile(Path tempDir, String... packs) throws IOException {
        final String packList = String.join("\",\"", packs);
        return Files.writeString(tempDir.resolve("vanillatweaks.json"),
            "{\"type\":\"datapacks\",\"version\":\"1.21\",\"packs\":{\"convenience\":[\"" + packList + "\"]}}"
        );
    }

    private static byte[] zipOf(String... entryNames) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(entryName.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
