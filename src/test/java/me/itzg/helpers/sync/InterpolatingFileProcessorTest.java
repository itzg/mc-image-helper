package me.itzg.helpers.sync;

import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.EnvironmentVariablesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InterpolatingFileProcessorTest {

    @Mock
    FileProcessor fallbackProcessor;

    @Test
    void processFileEnv(@TempDir Path tempDir) throws IOException {
        EnvironmentVariablesProvider environmentVariablesProvider = mock(EnvironmentVariablesProvider.class);
        // This will be called many times, so be lenient about mismatched mocks
        lenient().when(environmentVariablesProvider.get("CFG_VELOCITY_SECRET")).thenReturn("SuperSecretValue");

        ReplaceEnvOptions replaceEnvOptions = new ReplaceEnvOptions();
        replaceEnvOptions.suffixes = Collections.singletonList("yml");

        final Path src_env = Paths.get("src/test/resources/paper-env.yml");
        final Path src_env_out = Paths.get("src/test/resources/paper-out.yml");
        final Path dest_env = tempDir.resolve("paper-env.yml");

        final InterpolatingFileProcessor processor = new InterpolatingFileProcessor(
                replaceEnvOptions,
                new Interpolator(environmentVariablesProvider, "CFG_"),
                fallbackProcessor
        );

        processor.processFile(src_env, dest_env);

        assertThat(dest_env).exists();
        assertThat(dest_env).hasSameTextualContentAs(src_env_out, StandardCharsets.UTF_8);

        verifyNoInteractions(fallbackProcessor);
    }

    @Test
    void processFile(@TempDir Path tempDir) throws IOException {
        ReplaceEnvOptions replaceEnvOptions = new ReplaceEnvOptions();
        replaceEnvOptions.suffixes = Collections.singletonList("yml");

        final Path src = Paths.get("src/test/resources/paper.yml");
        final Path dest = tempDir.resolve("paper.yml");

        final InterpolatingFileProcessor processor = new InterpolatingFileProcessor(
                replaceEnvOptions,
                new Interpolator((name) -> name, "CFG_"),
                fallbackProcessor
        );

        processor.processFile(src, dest);

        assertThat(dest).exists();
        assertThat(dest).hasSameTextualContentAs(src, StandardCharsets.UTF_8);

        // simulate a newer change happening in destination file
        try (BufferedWriter writer = Files.newBufferedWriter(dest, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writer.write("\nextra: true\n");
        }

        processor.processFile(src, dest);

        assertThat(dest).exists();
        assertThat(dest).hasSameTextualContentAs(src, StandardCharsets.UTF_8);

        verifyNoInteractions(fallbackProcessor);
    }
}