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
import java.util.Arrays;
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

        @Test
        void oneWithInlineDestination() throws IOException {
            final Path srcFile = writeLine(tempDir, "source2.txt", "content");
            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "dest<" +srcFile.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("source2.txt"))
                .exists()
                .hasSameTextualContentAs(srcFile);
        }

        @Test
        void commaDelimited() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    String.join(",", srcTxt.toString(), srcJar.toString())
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }

        @Test
        void commaDelimitedMultipleDest() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    String.join(",",
                        "dest1<" + srcTxt.toString(),
                        "dest2<" + srcJar.toString()
                    )
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir2.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }

        @Test
        void commaDelimitedRelativeDest() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    String.join(",",
                        "./dest1<" + srcTxt.toString(),
                        "./dest2<" + srcJar.toString()
                    )
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir2.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }
    }

    @Nested
    class FileListingSrc {
        @Test
        void justFiles() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");
            final Path listing = Files.write(tempDir.resolve("listing.txt"), Arrays.asList(
                srcTxt.toString(),
                srcJar.toString()
            ));

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--file-is-listing",
                    listing.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
        }

        @Test
        void justFilesWithDifferentDest() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            final Path listing = Files.write(tempDir.resolve("listing.txt"), Arrays.asList(
                "dest1<" + srcTxt.toString(),
                "dest2<" + srcJar.toString()
            ));

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "--file-is-listing",
                    listing.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir2.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
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
        void toInlineDir() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "dest<" + srcDir
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
        void managedWithManifestAndMultipleDest() throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");
            final Path destTxt = destDir1.resolve("one.txt");
            final Path destJar = destDir2.resolve("two.jar");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "--scope", "managedWithManifest",
                    "dest1<" + srcTxt + "," + "dest2<" + srcJar
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destTxt)
                .hasSameTextualContentAs(srcTxt);
            assertThat(destJar)
                .hasSameTextualContentAs(srcJar);
            assertThat(destDir1.resolve("one.txt"))
                .hasSameTextualContentAs(srcTxt);
            assertThat(destDir2.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);

            Files.delete(srcTxt);
            assertThat(
                new CommandLine(new MulitCopyCommand())
                    .execute(
                        "--to", tempDir.toString(),
                        "--scope", "managedWithManifest",
                        destDir2 + "<" + srcJar
                    )
            ).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destTxt)
                .doesNotExist();
            assertThat(destDir2.resolve("two.jar"))
                .hasSameTextualContentAs(srcJar);
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
            stubRemoteSrc("file.jar", "remote");

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

        @Test
        void remoteFileWithInlineDest(WireMockRuntimeInfo wmInfo) {
            stubRemoteSrc("file.jar", "remote");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "dest<" + wmInfo.getHttpBaseUrl() + "/file.jar"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("file.jar"))
                .hasContent("remote");
        }

        @Test
        void listingOfRemoteFiles(WireMockRuntimeInfo wmInfo) throws IOException {
            stubRemoteSrc("file1.jar", "one");
            stubRemoteSrc("file2.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final Path listing = Files.write(tempDir.resolve("listing.txt"), Arrays.asList(
                wmInfo.getHttpBaseUrl() + "/file1.jar",
                wmInfo.getHttpBaseUrl() + "/file2.jar"
            ));

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--file-is-listing",
                    listing.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("file1.jar"))
                .hasContent("one");
            assertThat(destDir.resolve("file2.jar"))
                .hasContent("two");
        }

        @Test
        void listingOfRemoteFilesWithInlineDest(WireMockRuntimeInfo wmInfo) throws IOException {
            stubRemoteSrc("file1.jar", "one");
            stubRemoteSrc("file2.jar", "two");

            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            final Path listing = Files.write(tempDir.resolve("listing.txt"), Arrays.asList(
                "dest1<" + wmInfo.getHttpBaseUrl() + "/file1.jar",
                "dest2<" +wmInfo.getHttpBaseUrl() + "/file2.jar"
            ));

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "--file-is-listing",
                    listing.toString()
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("file1.jar"))
                .hasContent("one");
            assertThat(destDir2.resolve("file2.jar"))
                .hasContent("two");
        }

        @Test
        void remoteListingOfRemoteFiles(WireMockRuntimeInfo wmInfo) {
            stubRemoteSrc("listing.txt",
                wmInfo.getHttpBaseUrl() + "/file1.jar\n" +
                    wmInfo.getHttpBaseUrl() + "/file2.jar\n"
            );
            stubRemoteSrc("file1.jar", "one");
            stubRemoteSrc("file2.jar", "two");

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--file-is-listing",
                    wmInfo.getHttpBaseUrl() + "/listing.txt"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("file1.jar"))
                .hasContent("one");
            assertThat(destDir.resolve("file2.jar"))
                .hasContent("two");
        }

        @Test
        void remoteListingOfRemoteFilesWithInlineDest(WireMockRuntimeInfo wmInfo) {
            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            stubRemoteSrc("listing.txt",
                "dest1<" + wmInfo.getHttpBaseUrl() + "/file1.jar\n" +
                    "dest2<" + wmInfo.getHttpBaseUrl() + "/file2.jar\n"
            );
            stubRemoteSrc("file1.jar", "one");
            stubRemoteSrc("file2.jar", "two");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "--file-is-listing",
                    wmInfo.getHttpBaseUrl() + "/listing.txt"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("file1.jar"))
                .hasContent("one");
            assertThat(destDir2.resolve("file2.jar"))
                .hasContent("two");
        }

        @Test
        void remoteListingOfLocalFiles(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            stubRemoteSrc("listing.txt",
                srcTxt + "\n" +
                srcJar + "\n"
            );

            final Path destDir = tempDir.resolve("dest");

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir.toString(),
                    "--file-is-listing",
                    wmInfo.getHttpBaseUrl() + "/listing.txt"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir.resolve("one.txt"))
                .hasContent("one");
            assertThat(destDir.resolve("two.jar"))
                .hasContent("two");
        }

        @Test
        void remoteListingOfLocalFilesWithInlineDest(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");

            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");

            stubRemoteSrc("listing.txt",
                "dest1<" + srcTxt + "\n" +
                    "dest2<" +  srcJar + "\n"
            );

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", tempDir.toString(),
                    "--file-is-listing",
                    wmInfo.getHttpBaseUrl() + "/listing.txt"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir1.resolve("one.txt"))
                .hasContent("one");
            assertThat(destDir2.resolve("two.jar"))
                .hasContent("two");
        }

        @Test
        void overrideDestOrder(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path destDir1 = tempDir.resolve("dest1");
            final Path destDir2 = tempDir.resolve("dest2");
            final Path destDir3 = tempDir.resolve("dest3");

            final Path srcDir = Files.createDirectories(tempDir.resolve("srcDir"));
            final Path srcTxt = writeLine(srcDir, "one.txt", "one");
            final Path srcJar = writeLine(srcDir, "two.jar", "two");
            final Path srcYaml = writeLine(srcDir, "three.yaml", "three");

            stubRemoteSrc("listing.txt",
                srcTxt + "\n" +
                    srcJar + "\n" +
                    destDir3 + "<" +  srcYaml + "\n"
            );

            final int exitCode = new CommandLine(new MulitCopyCommand())
                .execute(
                    "--to", destDir1.toString(),
                    "--file-is-listing",
                    destDir2 + "<" + wmInfo.getHttpBaseUrl() + "/listing.txt"
                );
            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            assertThat(destDir2.resolve("one.txt"))
                .hasContent("one");
            assertThat(destDir2.resolve("two.jar"))
                .hasContent("two");
            assertThat(destDir3.resolve("three.yaml"))
                .hasContent("three");
        }

        private void stubRemoteSrc(String filename, String content) {
            stubFor(head(urlPathEqualTo("/" + filename))
                .willReturn(
                    noContent()
                        .withHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                )
            );
            stubFor(get("/" + filename)
                .willReturn(
                    ok(content)
                )
            );
        }
    }
}