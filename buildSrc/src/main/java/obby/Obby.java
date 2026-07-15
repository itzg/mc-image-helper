package obby;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Map;
import java.util.Properties;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class Obby {

    public static final String PROP_KEY = "_key";
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LEN = 16;
    private static final String KEY_TYPE = "AES";
    private final Map<String, String> propertiesClearText;

    public Obby(Map<String, String> propertiesClearText) {
        this.propertiesClearText = propertiesClearText;
    }

    public void writeToFile(Path file) throws IOException {
        final Properties props = new Properties();
        final Encoder base64Encoder = Base64.getUrlEncoder()
            .withoutPadding();

        final KeyGenerator keyGen;
        try {
            keyGen = KeyGenerator.getInstance(KEY_TYPE);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyGen.init(128);
        final SecretKey secretKey = keyGen.generateKey();

        try {
            propertiesClearText.forEach((k, v) -> {
                try {
                    props.setProperty(k,
                        base64Encoder.encodeToString(encrypt(v, secretKey))
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String dynamicKey = base64Encoder
            .encodeToString(secretKey.getEncoded());

        props.setProperty(PROP_KEY, dynamicKey);

        try (var out = Files.newOutputStream(file)) {
            props.store(out, null);
        }
    }

    private static byte[] encrypt(String v, SecretKey secretKey) throws IllegalBlockSizeException, BadPaddingException {
        byte[] iv = new byte[IV_LEN];
        new java.security.SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }

        byte[] ciphertext = cipher.doFinal(v.getBytes(StandardCharsets.UTF_8));

        byte[] packed = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);

        return packed;
    }

}
