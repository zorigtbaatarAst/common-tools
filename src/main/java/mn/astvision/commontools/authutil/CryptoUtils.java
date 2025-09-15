package mn.astvision.commontools.authutil;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * @author zorigtbaatar
 */

@Slf4j
public class CryptoUtils {
    //    @Value("${crypto.secret.key}")
    private static final String SECRET_KEY = "0123456789abcdef"; // 16 digit 32 bit value
    private static final String ALGORITHM = "AES";

    private static SecretKeySpec getSecretKey() {
        return new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
    }

    public static String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            String base64 = Base64.getEncoder().encodeToString(encrypted);
            return toBase64Url(base64);
        } catch (Exception e) {
            log.error("Encryption error", e);
            throw new RuntimeException("Encryption error", e);
        }
    }

    public static boolean isValidEncrypted(String encryptedText) {
        try {
            decrypt(encryptedText);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalArgumentException("Encrypted text must not be null or blank");
        }

        try {
            String base64 = fromBase64Url(encryptedText);
            byte[] decodedBytes = Base64.getDecoder().decode(base64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());

            byte[] decrypted = cipher.doFinal(decodedBytes);
            return new String(decrypted);
        } catch (Exception e) {
            log.error("Decryption error", e);
            throw new RuntimeException("Decryption error: possibly invalid or tampered ciphertext", e);
        }
    }

    // Utility: Convert standard Base64 to Base64URL (URL-safe)
    private static String toBase64Url(String base64) {
        return base64.replace("+", "-").replace("/", "_").replace("=", ""); // optional padding removal
    }

    // Utility: Convert Base64URL back to standard Base64
    private static String fromBase64Url(String base64Url) {
        String base64 = base64Url.replace("-", "+").replace("_", "/");

        // Add padding if needed
        int padding = (4 - base64.length() % 4) % 4;
        return base64 + "=".repeat(padding);
    }

    // Utility: Get SecretKeySpec from a 16/24/32-byte key for AES-128/192/256
    // Note: the key must be 16/24/32 bytes long (128/192/256 bits)
    // Note: the key must be encoded in Base64 (not Base64URL)
    // Note: the key must be UTF-8 encoded
    // Note: the key must not be null or blank

    @PostConstruct
    public void validateSecretKey() {
        if (SECRET_KEY == null || SECRET_KEY.isBlank()) {
            throw new IllegalArgumentException("Invalid secret key. Must not be null or blank. ");
        }

        if (SECRET_KEY.length() != 16 && SECRET_KEY.length() != 24 && SECRET_KEY.length() != 32) {
            throw new IllegalArgumentException("Invalid secret key length: must be 16, 24 or 32 bytes");
        }
    }
}
