package me.itzg.helpers.sync;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class MulitCopyCommandTest {

    @TempDir
    Path tempDir;

    private Path writeLine(Path dir, String name, String line) throws IOException {
        return Files.write(dir.resolve(name), Collections.singletonList(line));
    }

    @Nested
    class FileSrc {
        @Test
        void one() throws IOException {
            final Path srcFile = writeLine(tempDir, "source.txt", "content");
            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    srcFile.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("source.txt"))
                .exists()
                .hasSameTextualContentAs(srcFile);
        }
    }

    @Nested
    class DirectorySrc {
        @Test
        void noGlob() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    srcDir.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }

        @Test
        void handlesUpdatedFile() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");
            final Path destTxt = destDir.resolve("one.txt");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    srcDir.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destTxt)
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);

            Files.setLastModifiedTime(destTxt, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
            writeLine(srcDir, "one.txt", "updated");

            assertThat(
                new CommandLine(new MulitCopyCommand())
                    .execute(
                        "--to", destDir.toString(),
                        srcDir.toString()
                    )
            ).isEqualTo(CommandLine.ExitCode.OK);
            assertThat(destTxt)
                .hasContent("updated");
        }

        @Test
        void managedWithManifest() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");
            final Path destTxt = destDir.resolve("one.txt");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--scope", "managedWithManifest",
                    srcDir.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destTxt)
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);

            Files.delete(srcTxt);
            assertThat(
                new CommandLine(new MulitCopyCommand())
                    .execute(
                        "--to", destDir.toString(),
                        "--scope", "managedWithManifest",
                        srcDir.toString()
                    )
            ).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destTxt)
                .doesNotExist();
        }

        @Test
        void withGlob() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--glob", "*.jar",
                    srcDir.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("one.txt"))
                .doesNotExist();
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }
    }

    @Nested
    @WireMockTest
    class RemoteSrc {
        @Test
        void remoteFile(WireMockRuntimeInfo wmInfo) {
            stubFor(head(urlPathEqualTo("/file.jar"))
                .willReturn(
                    noContent()
                        .withHeader("Content-Disposition", "attachment; filename=\"file.jar\"")
                )
            );
            stubFor(get("/file.jar")
                .willReturn(
                    ok("remote")
                )
            );

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    wmInfo.getHttpBaseUrl() + "/file.jar"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("file.jar"))
                .hasContent("remote");
        }
    }
}