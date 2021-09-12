package me.itzg.helpers;

import java.io.IOException;
import java.nio.ByteBuffer;
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

    public static Charset detect(byte[] content) throws IOException {
        for (Charset c : KNOWN_CHARSETS) {
            final CharsetDecoder decoder = c.newDecoder();
            try {
                decoder.decode(ByteBuffer.wrap(content));
                return c;
            } catch (CharacterCodingException e) {
                // not this one
            }
        }
        throw new IOException("Unknown character encoding. Tried "+ Arrays.toString(KNOWN_CHARSETS));
    }
}
