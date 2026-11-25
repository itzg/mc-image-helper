package me.itzg.helpers.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.archive.ArchiveTestUtils;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class MultiCopyCommandArchiveTest {

    @TempDir
    Path tempDir;

    private static final Map<String, String> WORLD = Map.of(
        "saves/", "",
        "saves/world/level.dat", "level",
        "saves/world/region/r.0.0.mca", "region",
        "saves/other/level.dat", "other",
        "readme.txt", "readme"
    );

    /**
     * Modification time of every archive entry
     */
    private static final Instant ENTRY_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void extractsWholeZip() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "worlds<<" + archive);

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("worlds/saves/world/level.dat")).hasContent("level");
        assertThat(destDir.resolve("worlds/saves/other/level.dat")).hasContent("other");
        assertThat(destDir.resolve("worlds/readme.txt")).hasContent("readme");
    }

    @Test
    void extractsDirectoryWithPrefixRemoved() throws IOException {
        final Path archive = createTarGz(tempDir.resolve("world.tar.gz"), WORLD);
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "extracted<<" + archive + "{saves/world/}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("extracted/level.dat")).hasContent("level");
        assertThat(destDir.resolve("extracted/region/r.0.0.mca")).hasContent("region");
        assertThat(destDir.resolve("extracted/readme.txt")).doesNotExist();
        assertThat(destDir.resolve("extracted/saves")).doesNotExist();
        assertThat(destDir.resolve("other")).doesNotExist();
    }

    @Test
    void extractsSeveralDirectories() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(),
            "world<<" + archive + "{saves/world/region;saves/other}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("world/r.0.0.mca")).hasContent("region");
        assertThat(destDir.resolve("world/level.dat")).hasContent("other");
        assertThat(destDir.resolve("world/readme.txt")).doesNotExist();
    }

    @Test
    void extractsDirectoryOfEntriesWithCurrentDirectoryPrefix() throws IOException {
        final Path archive = createTarGz(tempDir.resolve("world.tar.gz"), Map.of(
            "./level.dat", "level",
            "./region/r.0.0.mca", "region"
        ));
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "world<<" + archive + "{region}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("world/r.0.0.mca")).hasContent("region");
        assertThat(destDir.resolve("world/level.dat")).doesNotExist();
    }

    @Test
    void extractsIntoOutputDirectoryWithoutDestination() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "<<" + archive + "{saves/other}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("level.dat")).hasContent("other");
    }

    @Test
    void extractsIntoAbsoluteDestination() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path otherDir = tempDir.resolve("other");

        final int exitCode = execute("--to", tempDir.resolve("dest").toString(),
            otherDir + "<<" + archive + "{saves/other}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(otherDir.resolve("level.dat")).hasContent("other");
    }

    @Test
    void ignoresGlob() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "--glob", "*.txt",
            "world<<" + archive + "{saves/world}");

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("world/level.dat")).hasContent("level");
    }

    @Test
    void extractsFromListingFile() throws IOException {
        final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
        final Path listing = Files.write(tempDir.resolve("listing.txt"), List.of(
            "# comment",
            "world<<" + archive + "{saves/world}"
        ));
        final Path destDir = tempDir.resolve("dest");

        final int exitCode = execute("--to", destDir.toString(), "--file-is-listing", listing.toString());

        assertThat(exitCode).isEqualTo(ExitCode.OK);
        assertThat(destDir.resolve("world/level.dat")).hasContent("level");
    }

    @Nested
    class ExistingFiles {

        private Path archive;
        private Path existing;

        private Path prepare(String content, Instant modified) throws IOException {
            archive = createZip(tempDir.resolve("world.zip"), WORLD);
            existing = Files.createDirectories(tempDir.resolve("dest/world")).resolve("level.dat");
            Files.writeString(existing, content);
            Files.setLastModifiedTime(existing, FileTime.from(modified));
            return archive;
        }

        private int extract(String... options) {
            return execute(concat(List.of(options),
                "--to", tempDir.resolve("dest").toString(), "world<<" + archive + "{saves/world}"));
        }

        @Test
        void replacedWithoutSkipOptions() throws IOException {
            prepare("changed by server", Instant.now());

            assertThat(extract("--skip-up-to-date=false")).isEqualTo(ExitCode.OK);

            assertThat(existing).hasContent("level");
        }

        @Test
        void keptWithSkipExisting() throws IOException {
            prepare("changed by server", ENTRY_TIME.minusSeconds(86400));

            assertThat(extract("--skip-existing")).isEqualTo(ExitCode.OK);
            assertThat(existing).hasContent("changed by server");
            assertThat(tempDir.resolve("dest/world/region/r.0.0.mca")).hasContent("region");

            assertThat(extract("--skip-existing", "--skip-up-to-date=false")).isEqualTo(ExitCode.OK);
            assertThat(existing).hasContent("changed by server");
            assertThat(tempDir.resolve("dest/world/region/r.0.0.mca")).hasContent("region");
        }

        @Test
        void olderWithDifferentSizeReplacedWhenSkippingUpToDate() throws IOException {
            prepare("old", ENTRY_TIME.minusSeconds(86400));

            assertThat(extract()).isEqualTo(ExitCode.OK);

            assertThat(existing).hasContent("level");
        }

        @Test
        void newerKeptWhenSkippingUpToDate() throws IOException {
            prepare("changed by server", ENTRY_TIME.plusSeconds(86400));

            assertThat(extract()).isEqualTo(ExitCode.OK);

            assertThat(existing).hasContent("changed by server");
            assertThat(tempDir.resolve("dest/world/region/r.0.0.mca")).hasContent("region");
        }

        @Test
        void sameSizeKeptWhenSkippingUpToDate() throws IOException {
            // TAR entries have a known size before reading, unlike ZIP entries with a data descriptor
            archive = createTarGz(tempDir.resolve("world.tar.gz"), WORLD);
            existing = Files.createDirectories(tempDir.resolve("dest/world")).resolve("level.dat");
            Files.writeString(existing, "LEVEL");
            Files.setLastModifiedTime(existing, FileTime.from(ENTRY_TIME.minusSeconds(86400)));

            assertThat(extract()).isEqualTo(ExitCode.OK);

            assertThat(existing).hasContent("LEVEL");
        }
    }

    @Nested
    class Manifest {

        @Test
        void includesExtractedFiles() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            final Path destDir = tempDir.resolve("dest");

            assertThat(execute("--to", destDir.toString(), "--scope", "extra",
                "world<<" + archive + "{saves/world}"))
                .isEqualTo(ExitCode.OK);

            assertThat(manifestFiles(destDir))
                .containsExactlyInAnyOrder(
                    Path.of("world", "level.dat").toString(),
                    Path.of("world", "region", "r.0.0.mca").toString()
                );

            assertThat(execute("--to", destDir.toString(), "--scope", "extra"))
                .isEqualTo(ExitCode.OK);

            assertThat(destDir.resolve("world/level.dat")).doesNotExist();
            assertThat(destDir.resolve("world/region/r.0.0.mca")).doesNotExist();
        }

        @Test
        void includesSkippedExistingFiles() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            final Path destDir = tempDir.resolve("dest");
            final Path existing = Files.createDirectories(destDir.resolve("world")).resolve("level.dat");
            Files.writeString(existing, "changed by server");

            assertThat(execute("--to", destDir.toString(), "--scope", "extra", "--skip-existing",
                "world<<" + archive + "{saves/world}"))
                .isEqualTo(ExitCode.OK);

            assertThat(existing).hasContent("changed by server");
            assertThat(manifestFiles(destDir))
                .containsExactlyInAnyOrder(
                    Path.of("world", "level.dat").toString(),
                    Path.of("world", "region", "r.0.0.mca").toString()
                );
        }

        @Test
        void keepsFilesSkippedOnLaterRuns() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            final Path destDir = tempDir.resolve("dest");
            final String[] args = {"--to", destDir.toString(), "--scope", "extra", "world<<" + archive + "{saves/world}"};
            final List<String> expected = List.of(
                Path.of("world", "level.dat").toString(),
                Path.of("world", "region", "r.0.0.mca").toString()
            );

            assertThat(execute(args)).isEqualTo(ExitCode.OK);
            assertThat(manifestFiles(destDir)).containsExactlyInAnyOrderElementsOf(expected);

            Files.writeString(destDir.resolve("world/level.dat"), "changed by server");

            // every entry is skipped now since the files are newer than the entries
            assertThat(execute(args)).isEqualTo(ExitCode.OK);
            assertThat(manifestFiles(destDir)).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(destDir.resolve("world/level.dat")).hasContent("changed by server");
        }

        @Test
        void withRelativeOutputDirectory() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            final Path relativeDest = Path.of("").toAbsolutePath().relativize(tempDir.resolve("dest"));
            final String to = "." + relativeDest.getFileSystem().getSeparator() + relativeDest;

            assertThat(execute("--to", to, "--scope", "extra", "world<<" + archive + "{saves/world}"))
                .isEqualTo(ExitCode.OK);

            final MultiCopyManifest manifest = Manifests.load(Path.of(to), "extra", MultiCopyManifest.class);
            assertThat(manifest).isNotNull();
            assertThat(manifest.getFiles())
                .containsExactlyInAnyOrder(
                    Path.of("world", "level.dat").toString(),
                    Path.of("world", "region", "r.0.0.mca").toString()
                );
        }
    }

    @Nested
    class Rejects {

        @Test
        void parentInArchivePath() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            assertRejected("world<<" + archive + "{saves/../saves/world}", InvalidParameterException.class);
        }

        @Test
        void absoluteArchivePath() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            assertRejected("world<<" + archive + "{/saves/world}", InvalidParameterException.class);
        }

        @Test
        void parentInArchiveDestination() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            assertRejected("../outside<<" + archive, InvalidParameterException.class);
            assertRejected("world/../../outside<<" + archive, InvalidParameterException.class);
            assertThat(tempDir.resolve("outside")).doesNotExist();
        }

        @Test
        void parentInFileDestination() throws IOException {
            final Path file = Files.writeString(tempDir.resolve("file.txt"), "file");
            assertRejected("../outside<" + file, InvalidParameterException.class);
            assertRejected("world\\..\\..\\outside<" + file, InvalidParameterException.class);
            assertThat(tempDir.resolve("outside")).doesNotExist();
        }

        @Test
        void missingDirectoryInArchive() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            assertRejected("world<<" + archive + "{saves/missing}", InvalidParameterException.class);
            assertRejected("world<<" + archive + "{saves/world;saves/missing}", InvalidParameterException.class);
            assertThat(tempDir.resolve("dest/world/level.dat")).doesNotExist();
        }

        @Test
        void pathTraversalWithinDirectory() throws IOException {
            // safe within the archive, but escapes the destination once the prefix is removed
            final Map<String, String> files = new LinkedHashMap<>();
            files.put("saves/world/level.dat", "level");
            files.put("saves/world/../../evil.txt", "evil");
            final Path archive = createZip(tempDir.resolve("evil.zip"), files);
            assertRejected("world<<" + archive + "{saves/world}", InvalidParameterException.class);
            assertThat(tempDir.resolve("dest/world/level.dat")).doesNotExist();
            assertThat(tempDir.resolve("dest/evil.txt")).doesNotExist();
        }

        @Test
        void missingArchive() {
            assertRejected("world<<" + tempDir.resolve("missing.zip"), InvalidParameterException.class);
        }

        @Test
        void notAnArchive() throws IOException {
            final Path file = Files.writeString(tempDir.resolve("plain.txt"), "plain");
            assertRejected("world<<" + file, InvalidParameterException.class);
        }

        @Test
        void pathTraversalInArchive() throws IOException {
            final Path archive = createZip(tempDir.resolve("evil.zip"), Map.of(
                "safe.txt", "safe",
                "../evil.txt", "evil"
            ));
            assertRejected("world<<" + archive, InvalidParameterException.class);
            assertThat(tempDir.resolve("dest/world/safe.txt")).doesNotExist();
            assertThat(tempDir.resolve("dest/evil.txt")).doesNotExist();
        }

        @Test
        void archiveAsListingFile() throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            assertRejected(InvalidParameterException.class, "--file-is-listing", "world<<" + archive);
        }

        private void assertRejected(String source, Class<? extends Exception> expected) {
            assertRejected(expected, source);
        }

        private void assertRejected(Class<? extends Exception> expected, String... args) {
            final LatchingExecutionExceptionHandler handler = new LatchingExecutionExceptionHandler();
            final int exitCode = new CommandLine(new MultiCopyCommand())
                .setExecutionExceptionHandler(handler)
                .execute(concat(List.of(args), "--to", tempDir.resolve("dest").toString()));

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
            assertThat(handler.getExecutionException()).isInstanceOf(expected);
        }
    }

    @Nested
    @WireMockTest
    class Remote {

        @Test
        void extractsRemoteArchive(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            stubFor(get("/world.zip").willReturn(ok().withBody(Files.readAllBytes(archive))));
            final Path destDir = tempDir.resolve("dest");

            final int exitCode = execute("--to", destDir.toString(),
                "world<<" + wmInfo.getHttpBaseUrl() + "/world.zip{saves/world}");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
            assertThat(destDir.resolve("world/level.dat")).hasContent("level");
            assertThat(destDir.resolve("world/region/r.0.0.mca")).hasContent("region");
            assertThat(destDir.resolve("world.zip")).doesNotExist();
            assertThat(destDir.resolve("world/world.zip")).doesNotExist();
            try (Stream<Path> cached = Files.list(destDir.resolve(".tmp/mcopy-archives"))) {
                assertThat(cached).hasSize(1);
            }
        }

        @Test
        void refetchesWithIfModifiedSince(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            stubFor(get("/world.zip").withHeader("If-Modified-Since", absent())
                .willReturn(ok().withBody(Files.readAllBytes(archive))));
            stubFor(get("/world.zip").withHeader("If-Modified-Since", matching(".+"))
                .willReturn(aResponse().withStatus(304)));
            final Path destDir = tempDir.resolve("dest");
            final String[] args = {"--to", destDir.toString(),
                "world<<" + wmInfo.getHttpBaseUrl() + "/world.zip{saves/world}"};

            assertThat(execute(args)).isEqualTo(ExitCode.OK);
            PathUtils.deleteDirectory(destDir.resolve("world"));

            assertThat(execute(args)).isEqualTo(ExitCode.OK);

            assertThat(destDir.resolve("world/level.dat")).hasContent("level");
            verify(1, getRequestedFor(urlEqualTo("/world.zip")).withHeader("If-Modified-Since", absent()));
            verify(1, getRequestedFor(urlEqualTo("/world.zip")).withHeader("If-Modified-Since", matching(".+")));
        }

        @Test
        void retrievesOnceForSeveralEntries(WireMockRuntimeInfo wmInfo) throws IOException {
            final Path archive = createZip(tempDir.resolve("world.zip"), WORLD);
            stubFor(get("/world.zip").willReturn(ok().withBody(Files.readAllBytes(archive))));
            final Path destDir = tempDir.resolve("dest");

            final int exitCode = execute("--to", destDir.toString(),
                "world<<" + wmInfo.getHttpBaseUrl() + "/world.zip{saves/world}",
                "other<<" + wmInfo.getHttpBaseUrl() + "/world.zip{saves/other}");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
            assertThat(destDir.resolve("world/level.dat")).hasContent("level");
            assertThat(destDir.resolve("other/level.dat")).hasContent("other");
            verify(1, getRequestedFor(urlEqualTo("/world.zip")));
        }

        @Test
        void ignoresMissingRemoteArchive(WireMockRuntimeInfo wmInfo) {
            stubFor(get("/missing.zip").willReturn(notFound()));
            final Path destDir = tempDir.resolve("dest");

            final int exitCode = execute("--to", destDir.toString(), "--ignore-missing-sources",
                "world<<" + wmInfo.getHttpBaseUrl() + "/missing.zip");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
            assertThat(destDir.resolve(".tmp/mcopy-archives")).isEmptyDirectory();
        }
    }

    private static List<String> manifestFiles(Path destDir) {
        final MultiCopyManifest manifest = Manifests.load(destDir, "extra", MultiCopyManifest.class);
        assertThat(manifest).isNotNull();
        return List.copyOf(manifest.getFiles());
    }

    private static int execute(String... args) {
        return new CommandLine(new MultiCopyCommand()).execute(args);
    }

    private static String[] concat(List<String> first, String... rest) {
        return Stream.concat(first.stream(), Arrays.stream(rest))
            .toArray(String[]::new);
    }

    private static Path createZip(Path archive, Map<String, String> files) throws IOException {
        return ArchiveTestUtils.createZip(archive, files, ENTRY_TIME);
    }

    private static Path createTarGz(Path archive, Map<String, String> files) throws IOException {
        return ArchiveTestUtils.createTarGz(archive, files, ENTRY_TIME);
    }
}
