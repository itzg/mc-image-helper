package me.itzg.helpers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CharsetDetectorTest {

    @Test
    void canDetectUtf8() throws IOException {
        // from https://en.wikipedia.org/wiki/UTF-8#Examples
        final byte[] content = new byte[]{
                0x24,
                (byte) 0xC2, (byte) 0xA2,
                (byte) 0xe0, (byte) 0xa4, (byte) 0xb9,
                // https://en.wikipedia.org/wiki/Hwair
                (byte) 0xf0, (byte) 0x90, (byte) 0x8d, (byte) 0x88
        };

        final CharsetDetector.Result result = CharsetDetector.detect(content);
        assertThat(result.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        // last one takes two UTF-16 characters, so takes up size=2
        assertThat(result.getContent().length()).isEqualTo(5);
        assertThat(result.getContent().toString()).isEqualTo("\u0024\u00a2\u0939\uD800\uDF48");
    }

    @Test
    void canDetectIso8859_1() throws IOException {
        // from https://en.wikipedia.org/wiki/UTF-8#Examples
        final byte[] content = new byte[]{
                // $
                0x24,
                // https://en.wikipedia.org/wiki/Cent_(currency)
                (byte) 0xa2,
                // https://en.wikipedia.org/wiki/%C3%9D
                (byte) 0xfd
        };

        final CharsetDetector.Result result = CharsetDetector.detect(content);
        assertThat(result.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(result.getContent().length()).isEqualTo(3);
        assertThat(result.getContent().toString()).isEqualTo("\u0024\u00a2\u00fd");
    }
}