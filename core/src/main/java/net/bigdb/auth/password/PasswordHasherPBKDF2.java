package net.bigdb.auth.password;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

/** PasswordHasher using the PBKDF2 (password based key derivation function)
 *  from RSA's PKCS5v2 (ftp://ftp.rsasecurity.com/pub/pkcs/pkcs-5v2/pkcs5v2-0.pdf).
 *  with Hmac-SHA1 as the Pseudo Number Generator.
 *  Salt is 16 bytes (128 bits), We use 10000 rounds.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 *
 */
public class PasswordHasherPBKDF2 implements PasswordHasher {
    private final static Logger logger =
            LoggerFactory.getLogger(PasswordHasherPBKDF2.class);

    private static final String NAME = "PBKDF2WithHmacSHA1";

    // The higher the number of iterations the more
    // expensive computing the hash is for us
    // and also for a brute force attack.
    private static final int DEFAULT_ITERATIONS = 10000;
    private static final int SALT_LENGTH = 16;
    private static final int DESIRED_KEY_LENGTH = 256;

    // secure random is thread safe
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Computes a salted PBKDF2 hash of given plaintext password suitable for
     * storing in a database. Empty passwords are not supported.
     */
    @Override
    public HashedPassword hashPassword(String password) {
        HashedPassword.Builder builder = new HashedPassword.Builder(NAME);

        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        builder.setSalt(encoding().encode(salt));
        builder.setRounds(DEFAULT_ITERATIONS);
        return builder.getHashInfo(hash(password, salt, DEFAULT_ITERATIONS));
    }


    @Override
    public boolean checkPassword(String clearPassword, HashedPassword hashed) {
        if(Strings.isNullOrEmpty(hashed.getSalt())) {
            logger.warn("Stored password has no salt");
            return false;
        }
        if(hashed.getRounds() == 0) {
            logger.warn("Stored password has no rounds");
            return false;
        }
        try {
            byte[] salt = encoding().decode(hashed.getSalt());
            String hashOfInput =
                hash(clearPassword, salt, hashed.getRounds());

            return hashOfInput.equals(hashed.getHashValue());

        } catch (IllegalArgumentException e) {
            logger.warn("Error checking stored hashed password: "+e.getMessage(), e);
            return false;
        }
    }

    private String hash(String password, byte[] salt, int rounds) {
        if (password == null)
            throw new IllegalArgumentException("Null passwords are not supported.");

        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance(NAME);
            SecretKey key =
                    f.generateSecret(new PBEKeySpec(password.toCharArray(), salt, rounds,
                            DESIRED_KEY_LENGTH));
            return encoding().encode(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Could not locate hashing function: "
                    + e.getMessage(), e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid key spec: " + e.getMessage(), e);
        }
    }

    private static BaseEncoding encoding() {
        return BaseEncoding.base64Url().omitPadding();
    }

    @Override
    public String getName() {
        return NAME;
    }
}
