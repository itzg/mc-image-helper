package me.itzg.helpers.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class InterpolatingFileVisitorTest {

    @Test
    void processFile(@TempDir Path tempDir) throws URISyntaxException, IOException {
        ReplaceEnvOptions replaceEnvOptions = new ReplaceEnvOptions();
        replaceEnvOptions.suffixes = Collections.singletonList("yml");

        final Path src = Paths.get("src/test/resources/paper.yml");
        final Path dest = tempDir.resolve("paper.yml");

        final InterpolatingFileVisitor visitor = new InterpolatingFileVisitor(
                src,
                dest,
                false,
                replaceEnvOptions,
                new Interpolator((name) -> name, "CFG_")
        );

        visitor.processFile(src, dest);

        assertThat(dest).exists();
        assertThat(dest).hasSameTextualContentAs(src, StandardCharsets.UTF_8);

        // simulate a newer change happening in destination file
        try (BufferedWriter writer = Files.newBufferedWriter(dest, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writer.write("\nextra: true\n");
        }

        visitor.processFile(src, dest);

        assertThat(dest).exists();
        assertThat(dest).hasSameTextualContentAs(src, StandardCharsets.UTF_8);
    }
}