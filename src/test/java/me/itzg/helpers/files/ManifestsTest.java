package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ManifestsTest {

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

    @Test
    void allFilesPresent_withWildcardIgnoreAll() {
        EmptyManifest manifest = EmptyManifest.builder()
            .files(Arrays.asList("a.jar", "b.jar"))
            .build();

        boolean result = Manifests.allFilesPresent(tempDir, manifest, Collections.singletonList("*"));
        assertThat(result).isTrue();
    }

    @Test
    void allFilesPresent_withGlobPattern() throws IOException {
        Files.createDirectories(tempDir.resolve("mods"));
        Files.createFile(tempDir.resolve("mods/present.jar"));

        EmptyManifest manifest = EmptyManifest.builder()
            .files(Arrays.asList("mods/present.jar", "mods/missing.jar"))
            .build();

        boolean result = Manifests.allFilesPresent(tempDir, manifest, Collections.singletonList("mods/*.jar"));
        assertThat(result).isTrue();
    }

    @Test
    void allFilesPresent_withExplicitFileNames() throws IOException {
        Files.createFile(tempDir.resolve("keep.jar"));

        EmptyManifest manifest = EmptyManifest.builder()
            .files(Arrays.asList("keep.jar", "remove.jar"))
            .build();

        boolean result = Manifests.allFilesPresent(tempDir, manifest, Collections.singletonList("remove.jar"));
        assertThat(result).isTrue();
    }
}
