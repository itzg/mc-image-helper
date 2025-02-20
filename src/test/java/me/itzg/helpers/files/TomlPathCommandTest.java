package me.itzg.helpers.files;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class TomlPathCommandTest {

    @ParameterizedTest
    @ValueSource(strings = {"$.bind", ".bind"})
    void extractsVelocityBind(String queryPath) throws Exception {
        final String out = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new TomlPathCommand())
                .execute(
                    queryPath,
                    Paths.get("src/test/resources/velocity.toml").toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

        });

        assertThat(out).isEqualTo("0.0.0.0:25565\n");
    }
}