package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.stefanbirkner.systemlambda.SystemLambda;

import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.McImageHelper;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public class ZipCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsZipWithZipSlip() throws IOException {
        final LatchingExecutionExceptionHandler exceptionHandler = new LatchingExecutionExceptionHandler();
        final Path slip = createTestZip(List.of("../../../../danger.txt"));

        final int exitCode = new CommandLine(new McImageHelper())
                .setExecutionExceptionHandler(exceptionHandler)
                .execute("zip", "check-zip-slip", slip.toString());

        assertThat(exceptionHandler.getExecutionException())
                .isInstanceOf(SecurityException.class)
                .hasMessageMatching("^Zip Slip detected at: .+? in zip: .+$");

        assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
    }

    @Test
    void acceptsValidZip() throws IOException, Exception {
        final Path slip = createTestZip(List.of("file.txt"));

        final String sysErr = SystemLambda.tapSystemErr(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                    .execute("zip", "check-zip-slip", slip.toString());

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(sysErr).isEmpty();
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