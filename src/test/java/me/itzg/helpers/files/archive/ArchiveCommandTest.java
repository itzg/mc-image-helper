package me.itzg.helpers.files.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

import com.github.stefanbirkner.systemlambda.SystemLambda;

import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public class ArchiveCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsZipWithZipSlip() throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path slip = createTestArchive(ArchiveType.ZIP, List.of("../../../../danger.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "check-path-traversal", slip.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(sysErr)
            .containsPattern("Zip slip detected at: .+? in zip: .+");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void acceptsValidArchive(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, List.of("file.txt"));

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
        final Path archive = createTestArchive(type, List.of("nested/file.txt"));
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
        final Path archive = createTestArchive(type, List.of("../danger.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(ArchiveException.class)
                .hasMessageContaining(type == ArchiveType.ZIP
                        ? "contains zip slip"
                        : "contains path traversal");
        assertThat(sysErr).contains("ArchiveException");
        assertThat(tempDir.resolve("danger.txt")).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void doesNotOverwriteExistingFilesByDefault(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, List.of("file.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.writeString(extractedFile, "existing");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(extractedFile).hasContent("existing");
    }

    @ParameterizedTest
    @EnumSource(ArchiveType.class)
    void overwritesExistingFilesWhenRequested(ArchiveType type) throws IOException, Exception {
        final Path archive = createTestArchive(type, List.of("file.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.writeString(extractedFile, "existing");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", archive.toString(), destination.toString(), "--overwrite");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(extractedFile).hasContent("data");
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
        Files.writeString(nonZip, "not a zip");

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

    Path createTestArchive(ArchiveType type, List<String> entries) throws IOException {
        return createTestArchive(type, List.of(), entries);
    }

    Path createTestArchive(ArchiveType type, List<String> directories, List<String> files) throws IOException {
        final Path archive = tempDir.resolve("test." + type.extension());

        switch (type) {
            case ZIP -> createZip(archive, directories, files);
            case TAR -> createTar(Files.newOutputStream(archive), directories, files);
            case TAR_GZIP -> createTar(
                    new GzipCompressorOutputStream(Files.newOutputStream(archive)), directories, files);
            case TAR_BZIP2 -> createTar(
                    new BZip2CompressorOutputStream(Files.newOutputStream(archive)), directories, files);
            case TAR_ZSTD -> createTar(
                    new ZstdCompressorOutputStream(Files.newOutputStream(archive)), directories, files);
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
        try (output; TarArchiveOutputStream tos = new TarArchiveOutputStream(output)) {
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
