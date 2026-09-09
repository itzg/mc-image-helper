package me.itzg.helpers.files.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.stefanbirkner.systemlambda.SystemLambda;

import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public class ZipCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsZipWithZipSlip() throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path slip = createTestZip(List.of("../../../../danger.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "check-path-traversal", slip.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(sysErr)
            .containsPattern("Zip slip detected at: .+? in zip: .+");
    }

    @Test
    void acceptsValidZip() throws IOException, Exception {
        final Path slip = createTestZip(List.of("file.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "check-path-traversal", slip.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
    }

    @Test
    void unzipsValidArchive() throws IOException, Exception {
        final Path zip = createTestZip(List.of("nested/file.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", zip.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(destination.resolve("nested/file.txt")).hasContent("data");
    }

    @Test
    void rejectsZipSlipDuringUnzip() throws IOException, Exception {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path zip = createTestZip(List.of("../danger.txt"));
        final Path destination = tempDir.resolve("destination");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .setExecutionExceptionHandler(exceptionHandler)
                    .execute("archive", "extract", zip.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(ArchiveException.class)
                .hasMessageContaining("contains zip slip");
        assertThat(sysErr).contains("ArchiveException");
        assertThat(tempDir.resolve("danger.txt")).doesNotExist();
    }

    @Test
    void doesNotOverwriteExistingFilesByDefault() throws IOException, Exception {
        final Path zip = createTestZip(List.of("file.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.writeString(extractedFile, "existing");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", zip.toString(), destination.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
        assertThat(extractedFile).hasContent("existing");
    }

    @Test
    void overwritesExistingFilesWhenRequested() throws IOException, Exception {
        final Path zip = createTestZip(List.of("file.txt"));
        final Path destination = Files.createDirectories(tempDir.resolve("destination"));
        final Path extractedFile = destination.resolve("file.txt");
        Files.writeString(extractedFile, "existing");

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("archive", "extract", zip.toString(), destination.toString(), "--overwrite");

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

    Path createTestZip(List<String> entries) throws IOException {
        final File zip = tempDir.resolve("test.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String path : entries) {
                ZipEntry entry = new ZipEntry(path);
                zos.putNextEntry(entry);
                zos.write("data".getBytes());
            }

            zos.close();
        }

        return zip.toPath();
    }

}