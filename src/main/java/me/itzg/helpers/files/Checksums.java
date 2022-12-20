package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import me.itzg.helpers.errors.GenericException;
import org.apache.commons.codec.binary.Hex;

public class Checksums {

    public static final String DEFAULT_PREFIX = "sha1";

    private static final Map<String/*prefix*/, String/*algo*/> prefixAlgos = new HashMap<>();
    private static final String DELIMITER = ":";

    static {
        prefixAlgos.put("md5", "MD5");
        prefixAlgos.put("sha1", "SHA-1");
        prefixAlgos.put("sha256", "SHA-256");
    }

    private Checksums() {
    }

    /**
     *
     * @param otherChecksum a checksum previously calculated with this method or null to use the default type
     * @param path a file to process
     * @return the file's checksum with a prefix tracking type
     */
    public static String checksumLike(String otherChecksum, Path path) throws IOException {
        final String algo;
        final String prefix;
        if (otherChecksum != null) {
            final String[] parts = otherChecksum.split(DELIMITER, 2);
            algo = prefixAlgos.get(parts[0]);
            if (algo == null) {
                throw new GenericException("Unexpected checksum prefix: " + parts[0]);
            }
            prefix = parts[0];
        }
        else {
            prefix = DEFAULT_PREFIX;
            algo = prefixAlgos.get(DEFAULT_PREFIX);
        }

        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            final byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) >= 0) {
                md.update(buffer, 0, len);
            }
        }

        final byte[] digest = md.digest();
        return prefix + DELIMITER + Hex.encodeHexString(digest);
    }
}
