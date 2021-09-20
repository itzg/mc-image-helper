package me.itzg.helpers;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CharsetDetector {
    public static final Charset[] KNOWN_CHARSETS = {
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1,
            StandardCharsets.US_ASCII,
            StandardCharsets.UTF_16
    };

    @Data @RequiredArgsConstructor
    public static class Result {
        final Charset charset;
        final CharBuffer content;
    }

    public static Result detect(byte[] content) throws IOException {
        for (Charset c : KNOWN_CHARSETS) {
            final CharsetDecoder decoder = c.newDecoder();
            try {
                return new Result(c, decoder.decode(ByteBuffer.wrap(content)));
            } catch (CharacterCodingException e) {
                // not this one
            }
        }
        throw new IOException("Unknown character encoding. Tried "+ Arrays.toString(KNOWN_CHARSETS));
    }
}
