package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Blocking;

public class Checksums {

    private Checksums() {
    }

    @Blocking
    public static boolean valid(Path file, ChecksumAlgo algo, String expectedCheckum) throws IOException {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(algo.getJdkAlgo());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            final byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) >= 0) {
                md.update(buffer, 0, len);
            }
        }
        final byte[] digest = md.digest();

        return expectedCheckum.toLowerCase().equals(Hex.encodeHexString(digest));
    }

}
