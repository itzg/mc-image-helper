package me.itzg.helpers.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import java.nio.file.Files;
import java.nio.file.Path;
import me.itzg.helpers.env.MappedEnvVarProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class InterpolateCommandTest {

    @Test
    void skipsLargeFiles(@TempDir Path tempDir) throws Exception {
        final long size = InterpolateCommand.FILE_SIZE_LIMIT + 1;

        final Path path = tempDir.resolve("largeFile.txt");
        try {
            Files.createFile(path);
            Files.write(path, new byte[(int) size]);
        } catch (Exception e) {
            fail("Failed to create large file", e);
        }

        final String out = SystemLambda.tapSystemErrAndOut(() -> {
            final int result = new CommandLine(
                new InterpolateCommand()
                    .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                        "CFG_FIELD", "not used"
                    ))
            )
                .execute(
                    "--replace-env-file-suffixes", "txt",
                    tempDir.toString()
                );

            assertThat(result).isEqualTo(ExitCode.OK);
        });

        assertThat(out).containsIgnoringCase("skipping");
    }
}