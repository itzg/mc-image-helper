package me.itzg.helpers.files.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.github.stefanbirkner.systemlambda.SystemLambda;

import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public class ArchiveCommandTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void rejectsArchiveWithPathTraversal(ArchiveType type) throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path slip = createTestArchive(type, Arrays.asList("../../../../danger.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "check-path-traversal", slip.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(sysErr)
            .containsPattern("Path traversal detected at: .+? in archive: .+");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void acceptsValidArchive(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, Arrays.asList("file.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "check-path-traversal", archive.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void extractsValidArchive(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, Arrays.asList("nested/file.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(destination.resolve("nested/file.txt")).hasContent("data");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void rejectsPathTraversalDuringExtraction(ArchiveType type) throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path archive = createTestArchive(type, Arrays.asList("../danger.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(ArchiveException.class)
                .hasMessageContaining("contains path traversal");
        assertThat(sysErr).contains("ArchiveException");
        assertThat(tempDir.resolve("danger.txt")).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void doesNotOverwriteExistingFilesByDefault(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, Arrays.asList("file.txt", "subsequent.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.write(extractedFile, "existing".getBytes(StandardCharsets.UTF_8));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(extractedFile).hasContent("existing");
        assertThat(destination.resolve("subsequent.txt")).hasContent("data");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void overwritesExistingFilesWhenRequested(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, Arrays.asList("file.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.write(extractedFile, "existing".getBytes(StandardCharsets.UTF_8));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString(), "--overwrite");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(extractedFile).hasContent("data");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void rejectsPathTraversalBeforeExtractingAnyEntries(ArchiveType type) throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path archive = createTestArchive(type, Arrays.asList("safe/file.txt", "../danger.txt"));
        final Path destination = tempDir.resolve("destination");

        SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException()).isInstanceOf(ArchiveException.class);
        assertThat(destination.resolve("safe/file.txt")).doesNotExist();
        assertThat(tempDir.resolve("danger.txt")).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void extractsExplicitDirectories(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type,
                Arrays.asList("nested/"), Arrays.asList("nested/file.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(destination.resolve("nested")).isDirectory();
        assertThat(destination.resolve("nested/file.txt")).hasContent("data");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void extractsEmptyArchive(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, Collections.<String>emptyList());
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(destination).isDirectory();
        try (Stream<Path> files = Files.list(destination)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void rejectsMissingZip() throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path missingZip = tempDir.resolve("missing.zip");
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", missingZip.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("File does not exist at");
        assertThat(sysErr).contains("InvalidParameterException");
    }

    @Test
    void rejectsNonZipFile() throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path nonZip = tempDir.resolve("not-a-zip.txt");
        final Path destination = tempDir.resolve("destination");
        Files.write(nonZip, "not a zip".getBytes(StandardCharsets.UTF_8));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", nonZip.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("File is not an archive/zip");
        assertThat(sysErr).contains("InvalidParameterException");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void extractsOnlySelectedFiles(ArchiveType type) throws Exception {
        final Path archive = createTestArchive(type, Arrays.asList("unused/"),
                Arrays.asList("before.txt", "nested/selected file.txt", "between.txt", "-last.txt"));

        for (List<String> selection : Arrays.asList(
                Arrays.asList("nested/selected file.txt"),
                Arrays.asList("-last.txt", "nested/selected file.txt", "nested/selected file.txt"))) {
            final Path destination = tempDir.resolve("destination-" + selection.size());
            final List<String> args = new ArrayList<>(Arrays.asList(
                    "archive", "extract", "--", archive.toString(), destination.toString()));
            args.addAll(selection);

            final String sysErr = SystemLambda.tapSystemErr(() ->
                    assertThat(new CommandLine(new McImageHelper()).execute(args.toArray(new String[args.size()])))
                            .isEqualTo(ExitCode.OK));

            assertThat(sysErr).isEmpty();
            assertThat(destination.resolve("nested/selected file.txt")).hasContent("data");
            assertThat(destination.resolve("before.txt")).doesNotExist();
            assertThat(destination.resolve("between.txt")).doesNotExist();
            assertThat(destination.resolve("unused")).doesNotExist();
            if (selection.contains("-last.txt")) {
                assertThat(destination.resolve("-last.txt")).hasContent("data");
            } else {
                assertThat(destination.resolve("-last.txt")).doesNotExist();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void rejectsMissingSelectedFilesBeforeCreatingDestination(ArchiveType type) throws Exception {
        final Path archive = createTestArchive(type, Arrays.asList("nested/"), Arrays.asList("file.txt"));
        final Path destination = tempDir.resolve("destination");
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();

        SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", archive.toString(), destination.toString(),
                            "file.txt", "missing.txt", "FILE.txt", "nested/");
            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(ArchiveException.class)
                .hasMessage("Files not found in archive: missing.txt, FILE.txt, nested/");
        assertThat(destination).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void rejectsUnselectedPathTraversalBeforeCreatingDestination(ArchiveType type) throws Exception {
        final Path archive = createTestArchive(type, Arrays.asList("safe.txt", "../danger.txt"));
        final Path destination = tempDir.resolve("destination");
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();

        SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", archive.toString(), destination.toString(), "safe.txt");
            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(ArchiveException.class)
                .hasMessageContaining("contains path traversal");
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve("danger.txt")).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void respectsOverwriteForSelectedFiles(ArchiveType type) throws Exception {
        final Path archive = createTestArchive(type, Arrays.asList("file.txt", "unselected.txt", "subsequent.txt"));

        for (boolean overwrite : Arrays.asList(false, true)) {
            final Path destination = Files.createDirectories(tempDir.resolve("destination-" + overwrite));
            Files.write(destination.resolve("file.txt"), "existing".getBytes(StandardCharsets.UTF_8));
            Files.write(destination.resolve("unselected.txt"), "untouched".getBytes(StandardCharsets.UTF_8));
            final List<String> args = new ArrayList<>(Arrays.asList("archive", "extract"));
            if (overwrite) {
                args.add("--overwrite");
            }
            args.addAll(Arrays.asList("--", archive.toString(), destination.toString(), "file.txt", "subsequent.txt"));

            final String sysErr = SystemLambda.tapSystemErr(() ->
                    assertThat(new CommandLine(new McImageHelper()).execute(args.toArray(new String[args.size()])))
                            .isEqualTo(ExitCode.OK));

            assertThat(sysErr).isEmpty();
            assertThat(destination.resolve("file.txt")).hasContent(overwrite ? "data" : "existing");
            assertThat(destination.resolve("unselected.txt")).hasContent("untouched");
            assertThat(destination.resolve("subsequent.txt")).hasContent("data");
        }
    }

    Path createTestArchive(ArchiveType type, List<String> entries) throws IOException {
        return createTestArchive(type, Collections.<String>emptyList(), entries);
    }

    Path createTestArchive(ArchiveType type, List<String> directories, List<String> files) throws IOException {
        final Path archive = tempDir.resolve("test." + type.extension());

        switch (type) {
            case ZIP:
                createZip(archive, directories, files);
                break;
            case TAR:
                createTar(Files.newOutputStream(archive), directories, files);
                break;
            case TAR_GZIP:
                createTar(new GzipCompressorOutputStream(Files.newOutputStream(archive)), directories, files);
                break;
            case TAR_BZIP2:
                createTar(new BZip2CompressorOutputStream(Files.newOutputStream(archive)), directories, files);
                break;
            case TAR_ZSTD:
                createTar(new ZstdCompressorOutputStream(Files.newOutputStream(archive)), directories, files);
                break;
            default:
                throw new IllegalArgumentException("Unsupported archive type: " + type);
        }

        return archive;
    }

    private void createZip(Path archive, List<String> directories, List<String> files) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String path : directories) {
                ZipEntry entry = new ZipEntry(path);
                zos.putNextEntry(entry);
                zos.closeEntry();
            }

            for (String path : files) {
                ZipEntry entry = new ZipEntry(path);
                zos.putNextEntry(entry);
                zos.write("data".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    private void createTar(OutputStream output, List<String> directories, List<String> files) throws IOException {
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(output)) {
            final byte[] data = "data".getBytes(StandardCharsets.UTF_8);

            for (String path : directories) {
                final String directoryPath = path.endsWith("/") ? path : path + "/";
                final TarArchiveEntry entry = new TarArchiveEntry(directoryPath);
                tos.putArchiveEntry(entry);
                tos.closeArchiveEntry();
            }

            for (String path : files) {
                TarArchiveEntry entry = new TarArchiveEntry(path);
                entry.setSize(data.length);
                tos.putArchiveEntry(entry);
                tos.write(data);
                tos.closeArchiveEntry();
            }
        }
    }

    enum ArchiveType {
        ZIP("zip"),
        TAR("tar"),
        TAR_GZIP("tar.gz"),
        TAR_BZIP2("tar.bz2"),
        TAR_ZSTD("tar.zst");

        private final String extension;

        ArchiveType(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }
    }

}
