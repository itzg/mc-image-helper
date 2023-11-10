package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestsTest {

    @TempDir
    Path tempDir;

    @Getter @SuperBuilder @Jacksonized
    static class EmptyManifest extends BaseManifest {

    }

    @Test
    void loadFailsGracefullyWhenInvalid() throws IOException {
        final String id = RandomStringUtils.randomAlphabetic(5);

        final Path manifestFile = tempDir.resolve(String.format(".%s-manifest.json", id));
        Files.write(manifestFile, Collections.singletonList("not json"));

        final EmptyManifest manifest = Manifests.load(tempDir, id, EmptyManifest.class);
        assertThat(manifest).isNull();
    }
}