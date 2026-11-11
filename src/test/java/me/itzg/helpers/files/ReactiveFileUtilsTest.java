package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReactiveFileUtilsTest {

    private static final Duration BLOCK = Duration.ofSeconds(5);

    @TempDir
    Path tempDir;

    @Test
    void isValidJarAcceptsReadableJar() throws IOException {
        final Path jar = tempDir.resolve("ok.jar");
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jar))) {
            jarOut.putNextEntry(new JarEntry("readme.txt"));
            jarOut.write("hello".getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }

        assertThat(ReactiveFileUtils.hasZipMagic(jar)).isTrue();
        assertThat(ReactiveFileUtils.isValidJar(jar)).isTrue();
        assertThat(ReactiveFileUtils.validJar(jar).block(BLOCK)).isEqualTo(jar);
    }

    @Test
    void isValidJarRejectsNonZipPayload() throws IOException {
        final Path file = tempDir.resolve("page.jar");
        Files.writeString(file, "<html><body>Not found</body></html>");

        assertThat(ReactiveFileUtils.hasZipMagic(file)).isFalse();
        assertThat(ReactiveFileUtils.isValidJar(file)).isFalse();
        assertThat(ReactiveFileUtils.validJar(file).block(BLOCK)).isNull();
    }

    @Test
    void isValidJarRejectsTruncatedZip() throws IOException {
        final Path file = tempDir.resolve("truncated.jar");
        // Local-file header magic without a central directory.
        Files.write(file, new byte[] {
            0x50, 0x4b, 0x03, 0x04,
            0x14, 0x00, 0x00, 0x00
        });

        assertThat(ReactiveFileUtils.hasZipMagic(file)).isTrue();
        assertThat(ReactiveFileUtils.isValidJar(file)).isFalse();
        assertThat(ReactiveFileUtils.validJar(file).block(BLOCK)).isNull();
    }

    @Test
    void isValidJarRejectsEmptyOrTooShortFile() throws IOException {
        final Path empty = tempDir.resolve("empty.jar");
        Files.write(empty, new byte[0]);
        final Path tooShort = tempDir.resolve("short.jar");
        Files.write(tooShort, new byte[] {0x50, 0x4b, 0x03});

        assertThat(ReactiveFileUtils.hasZipMagic(empty)).isFalse();
        assertThat(ReactiveFileUtils.isValidJar(empty)).isFalse();
        assertThat(ReactiveFileUtils.validJar(empty).block(BLOCK)).isNull();
        assertThat(ReactiveFileUtils.hasZipMagic(tooShort)).isFalse();
        assertThat(ReactiveFileUtils.isValidJar(tooShort)).isFalse();
        assertThat(ReactiveFileUtils.validJar(tooShort).block(BLOCK)).isNull();
    }

    @Test
    void hasZipMagicAcceptsLocalEmptyAndSpannedSignatures() throws IOException {
        assertThat(ReactiveFileUtils.hasZipMagic(header(0x03, 0x04))).isTrue();
        assertThat(ReactiveFileUtils.hasZipMagic(header(0x05, 0x06))).isTrue();
        assertThat(ReactiveFileUtils.hasZipMagic(header(0x07, 0x08))).isTrue();
        assertThat(ReactiveFileUtils.hasZipMagic(header(0x01, 0x02))).isFalse();
    }

    private Path header(int third, int fourth) throws IOException {
        final Path file = tempDir.resolve("header-" + third + "-" + fourth);
        Files.write(file, new byte[] {0x50, 0x4b, (byte) third, (byte) fourth});
        return file;
    }
}
