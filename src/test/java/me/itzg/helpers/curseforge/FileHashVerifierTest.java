package me.itzg.helpers.curseforge;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.itzg.helpers.curseforge.model.FileHash;
import me.itzg.helpers.curseforge.model.HashAlgo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("CodeBlock2Expr")
class FileHashVerifierTest {

    @Test
    void validMd5(@TempDir Path tempDir) throws Exception {
        final Path file = Files.write(tempDir.resolve("hello.txt"), "Hello World".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> {
            FileHashVerifier.verify(file, singletonList(
                new FileHash().setAlgo(HashAlgo.Md5).setValue("B10A8DB164E0754105B7A99BE72E3FE5")
            ))
                .block();
        })
            .doesNotThrowAnyException();
    }

    @Test
    void validSha1(@TempDir Path tempDir) throws IOException {
        final Path file = Files.write(tempDir.resolve("hello.txt"), "Hello World".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> {
            FileHashVerifier.verify(file, singletonList(
                new FileHash().setAlgo(HashAlgo.Sha1).setValue("0A4D55A8D778E5022FAB701977C5D840BBC486D0")
            ))
                .block();
        })
            .doesNotThrowAnyException();

    }

    @Test
    void handlesInvalid(@TempDir Path tempDir) throws IOException {
        final Path file = Files.write(tempDir.resolve("hello.txt"), "Hello World".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> {
            FileHashVerifier.verify(file, singletonList(
                new FileHash().setAlgo(HashAlgo.Md5).setValue("BAD")
            ))
                .block();
        })
            .isInstanceOf(FileHashInvalidException.class);
    }
}