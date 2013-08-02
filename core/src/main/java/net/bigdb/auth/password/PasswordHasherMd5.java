package net.bigdb.auth.password;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

/**
 * Simple passwordhasher that uses unsalted MD5. Don't use in production
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class PasswordHasherMd5 implements PasswordHasher {
    private static final String NAME = "md5";

    @Override
    public HashedPassword hashPassword(String clearPassword) {
        HashedPassword.Builder builder = new HashedPassword.Builder(NAME);
        return builder.getHashInfo(hash(clearPassword));
    }

    @Override
    public boolean checkPassword(String clearPassword, HashedPassword hashed) {
        return Objects.equal(hash(clearPassword), hashed.getHashValue());
    }

    private String hash(String clearPassword) {
        if (Strings.isNullOrEmpty(clearPassword)) {
            return "";
        } else {
            // note: Hashing.md5().hashString(str) without the charset parameter
            // does *not* use the default charset (as one might expect), but
            // hashes the char array directly, effectively resulting in a
            // UTF_16LE conversion that is incompatible with what other tools
            // (e.g., md5sum) produce.

            return Hashing.md5().hashString(clearPassword, Charsets.UTF_8).toString();
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
