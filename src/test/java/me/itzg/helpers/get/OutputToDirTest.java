package me.itzg.helpers.get;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@WireMockTest
class OutputToDirTest {

    @Test
    void saveFileFromGithubRelease(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        // 302 to CDN location
        // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
        stubFor(get(urlPathEqualTo("/github/releases/file.txt"))
            .willReturn(
                temporaryRedirect("/cdn/1-2-3-4")
            )
        );
        stubFor(get(urlPathEqualTo("/cdn/1-2-3-4"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-disposition", "attachment; filename=final-name.txt")
                .withHeader("content-type", "text/plain")
                .withBody("final content")
            )
        );

        final Path dontPruneThis = tempDir.resolve("keep.jar");
        Files.createFile(dontPruneThis);

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    wm.getHttpBaseUrl() + "/github/releases/file.txt"
                );

        final Path expectedFile = tempDir.resolve("final-name.txt");

        assertThat(status).isEqualTo(0);
        assertThat(expectedFile).exists();
        assertThat(expectedFile).hasContent("final content");
        assertThat(dontPruneThis).exists();
    }

    @Test
    void saveFileLikeBukkit(@TempDir Path tempDir, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/bukkit/123/download"))
            .willReturn(
                temporaryRedirect("/forgecdn/saveFileLikeBukkit.txt")
            )
        );
        stubFor(get(urlPathEqualTo("/forgecdn/saveFileLikeBukkit.txt"))
            .willReturn(ok("final content"))
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    wm.getHttpBaseUrl() + "/bukkit/123/download"
                );

        final Path expectedFile = tempDir.resolve("saveFileLikeBukkit.txt");

        assertThat(status).isEqualTo(0);
        assertThat(tempDir.resolve("download")).doesNotExist();
        assertThat(expectedFile).exists();
        assertThat(expectedFile).hasContent("final content");

    }

    @Test
    void multipleUrisSeparated(@TempDir Path tempDir, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/one"))
            .willReturn(
                temporaryRedirect("/one.txt")
            )
        );
        stubFor(get(urlPathEqualTo("/one.txt"))
            .willReturn(ok("content for one"))
        );
        stubFor(get(urlPathEqualTo("/two.txt"))
            .willReturn(ok("content for two"))
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    wm.getHttpBaseUrl() + "/one",
                    wm.getHttpBaseUrl() + "/two.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
        assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    }

    @Test
    void combinesWithUrisFile(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/one.txt"))
            .willReturn(ok("content for one"))
        );
        stubFor(get(urlPathEqualTo("/two.txt"))
            .willReturn(ok("content for two"))
        );
        stubFor(get(urlPathEqualTo("/%5B1.10.2%5Dthree.txt"))
            .willReturn(ok("content for three"))
        );

        final List<String> lines = Arrays.asList(
            wm.getHttpBaseUrl() + "/one.txt",
            "",
            "#" + wm.getHttpBaseUrl() + "/notThis.txt",
            wm.getHttpBaseUrl() + "/[1.10.2]three.txt"
        );
        final Path urisFile = Files.write(tempDir.resolve("uris.txt"), lines);

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o", tempDir.toString(),
                    "--uris-file", urisFile.toString(),
                    wm.getHttpBaseUrl() + "/two.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
        assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
        assertThat(tempDir.resolve("[1.10.2]three.txt")).hasContent("content for three");
    }

    @Test
    void prunesOthers(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        stubFor(head(urlPathEqualTo("/one.txt"))
            .willReturn(aResponse()
                .withStatus(204)
            )
        );
        stubFor(head(urlPathEqualTo("/two.txt"))
            .willReturn(aResponse()
                .withStatus(204)
            )
        );
        stubFor(get(urlPathEqualTo("/two.txt"))
            .willReturn(ok("content for two"))
        );

        final Path keep = Files.createFile(tempDir.resolve("keep.dat"));
        final Path pruneJar = Files.createFile(tempDir.resolve("prune.jar"));
        final Path keepJar = Files.createFile(Files.createDirectory(tempDir.resolve("inner"))
            .resolve("keep.jar"));

        // this one will be skipped
        final Path oneTxt = Files.createFile(tempDir.resolve("one.txt"));

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    "--skip-existing",
                    "--prune-others", "*.txt,*.jar",
                    // use default prune depth of 1
                    wm.getHttpBaseUrl() + "/one.txt",
                    wm.getHttpBaseUrl() + "/two.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(oneTxt).exists();
        assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
        assertThat(keep).exists();
        assertThat(keepJar).exists();
        assertThat(pruneJar).doesNotExist();
    }

    @Test
    void pruneDepthIsUsed(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/one.txt"))
            .willReturn(ok("content for one"))
        );

        final Path keepTxt = Files.createFile(tempDir.resolve("keep.txt"));
        final Path pruneTopJar = Files.createFile(tempDir.resolve("pruneTop.jar"));
        final Path outerDir = Files.createDirectory(tempDir.resolve("outer"));
        final Path pruneOuterJar = Files.createFile(outerDir.resolve("pruneOuter.jar"));
        final Path keepJar = Files.createFile(Files.createDirectory(outerDir.resolve("inner"))
            .resolve("keep.jar"));

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    "--prune-others", "*.jar",
                    "--prune-depth", "2",
                    wm.getHttpBaseUrl() + "/one.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
        assertThat(keepTxt).exists();
        assertThat(pruneOuterJar).doesNotExist();
        assertThat(pruneTopJar).doesNotExist();
        assertThat(keepJar).exists();
    }

    @Test
    void multipleUrisConcatenated(@TempDir Path tempDir, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/one.txt"))
            .willReturn(ok("content for one"))
        );
        stubFor(get(urlPathEqualTo("/two.txt"))
            .willReturn(ok("content for two"))
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    String.join(",",
                        wm.getHttpBaseUrl() + "/one.txt",
                        wm.getHttpBaseUrl() + "/two.txt"
                    )
                );

        assertThat(status).isEqualTo(0);
        assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
        assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    }

    @Test
    void skipExisting(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        stubFor(head(urlPathEqualTo("/one.txt"))
            .willReturn(noContent())
        );
        stubFor(get(urlPathEqualTo("/one.txt"))
            .willReturn(ok("new content for one"))
        );
        stubFor(head(urlPathEqualTo("/two.txt"))
            .willReturn(noContent())
        );
        stubFor(get(urlPathEqualTo("/two.txt"))
            .willReturn(ok("content for two"))
        );

        final Path fileOne = tempDir.resolve("one.txt");
        final Path fileTwo = tempDir.resolve("two.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(fileOne)) {
            writer.write("old content for one");
        }

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "-o",
                    tempDir.toString(),
                    "--skip-existing",
                    "--output-filename",
                    wm.getHttpBaseUrl() + "/one.txt",
                    wm.getHttpBaseUrl() + "/two.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(fileOne).hasContent("old content for one");
        assertThat(fileTwo).hasContent("content for two");

        final String[] parts = output.toString().split(lineSeparator());
        assertThat(parts).containsExactlyInAnyOrder(
            fileOne.toString(), fileTwo.toString()
        );
    }

    @Test
    void skipExistingWithContentDisposition(@TempDir Path tempDir, WireMockRuntimeInfo wm) {
        // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
        stubFor(get(urlPathEqualTo("/cdn/1-2-3-4"))
            .willReturn(ok()
                .withHeader("content-disposition", "attachment; filename=final-name.txt")
                .withBody("final content")
            )
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "-o",
                    tempDir.toString(),
                    wm.getHttpBaseUrl() + "/cdn/1-2-3-4"
                );

        final Path expectedFile = tempDir.resolve("final-name.txt");

        assertThat(status).isEqualTo(0);
        assertThat(expectedFile).exists();
        assertThat(expectedFile).hasContent("final content");
    }

    @Test
    void skipsUpToDate(@TempDir Path tempDir, WireMockRuntimeInfo wm) throws IOException {
        final Path fileToSkip = Files.createFile(tempDir.resolve("existing.txt"));
        // set it to a known time "in the past"
        Files.setLastModifiedTime(fileToSkip, FileTime.from(1637551412, TimeUnit.SECONDS));

        stubFor(head(urlPathEqualTo("/existing.txt"))
            .willReturn(noContent())
        );
        stubFor(get(urlPathEqualTo("/existing.txt"))
            .withHeader("if-modified-since", equalToDateTime("Mon, 22 Nov 2021 03:23:32 GMT"))
            .willReturn(status(304/*not modified*/))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "-o",
                    tempDir.toString(),
                    "--skip-up-to-date",
                    "--output-filename",
                    wm.getHttpBaseUrl() + "/existing.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(Files.getLastModifiedTime(fileToSkip).to(TimeUnit.SECONDS)).isEqualTo(1637551412);
    }

    @Test
    void doesntWriteFileWhenNotFound(@TempDir Path tempDir, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/doesntWriteFileWhenNotFound.txt"))
            .willReturn(notFound()
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>Not found</body></html>"))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "-o", tempDir.toString(),
                    wm.getHttpBaseUrl() + "/doesntWriteFileWhenNotFound.txt"
                );

        assertThat(status).isEqualTo(1);
        assertThat(tempDir).isEmptyDirectory();
    }

}
