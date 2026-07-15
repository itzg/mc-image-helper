package me.itzg.helpers.files;

import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ObbyLoader {

    public static final String PROP_KEY = "_key";
    /**
     * Matches the one used by ObbyTask
     */
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    /**
     * Matches the one used by ObbyTask
     */
    private static final int IV_LEN = 16;
    /**
     * Matches the one used by ObbyTask
     */
    private static final String KEY_TYPE = "AES";
    private static final Set<String> EXCLUDED_PROPERTIES = Set.of(PROP_KEY);

    public static Map<String, String> loadProperties(String path) {
        final Properties encryptedProps = new Properties();
        try (InputStream in = ObbyLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            encryptedProps.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return decrypt(encryptedProps);
    }

    private static Map<String, String> decrypt(Properties encryptedProps) {
        final Decoder decoder = Base64.getUrlDecoder();
        final byte[] key = decoder.decode(encryptedProps.getProperty(PROP_KEY));

        final SecretKeySpec secretKeySpec = new SecretKeySpec(key, KEY_TYPE);

        return encryptedProps.stringPropertyNames()
            .stream()
            .filter(name -> !EXCLUDED_PROPERTIES.contains(name))
            .map(name -> Map.entry(name,
                decrypt(
                    decoder.decode(encryptedProps.getProperty(name)),
                    secretKeySpec
                )
            ))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String decrypt(byte[] value, Key key) {
        try {
            final Cipher cipher = Cipher.getInstance(ObbyLoader.CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(value, 0, ObbyLoader.IV_LEN));

            cipher.update(value, ObbyLoader.IV_LEN, value.length - ObbyLoader.IV_LEN);
            return new String(cipher.doFinal(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }
}



