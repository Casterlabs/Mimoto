package co.casterlabs.mimoto.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.mindrot.BCrypt;

public class CryptoUtil {
    private static final char[] KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final int SECURE_KEY_SIZE = 128;
    private static final int ID_SIZE = 32;

    private static final SecureRandom random = new SecureRandom();

    public static char[] generateSecureRandomKey() {
        return generateKey(SECURE_KEY_SIZE, KEY_CHARS);
    }

    public static char[] generateRandomId() {
        return generateKey(ID_SIZE, KEY_CHARS);
    }

    private static char[] generateKey(int length, char[] chars) {
        char[] result = new char[length];

        for (int i = 0; i < length; i++) {
            result[i] = chars[random.nextInt(chars.length)];
        }

        return result;
    }

    public static String encrypt(String toEncrypt) {
        String bcrypt = BCrypt.hashpw(toEncrypt, BCrypt.gensalt());

        // We b64 encode the result to make sure it's URL safe.
        return Base64
            .getUrlEncoder()
            .encodeToString(bcrypt.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean decryptCompare(String encrypted, String challenge) {
        String bcrypt = new String(
            Base64
                .getUrlDecoder()
                .decode(encrypted),
            StandardCharsets.UTF_8
        );

        return BCrypt.checkpw(
            // These may seem reversed, but they aren't.
            challenge,
            bcrypt
        );
    }

}
