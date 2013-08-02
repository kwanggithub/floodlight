package net.bigdb.auth.password;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

/** Data object containing information about a hashed password. Supports serialization and deserialization from/to a string.
 *  string format:
 *   (${key}=${value},)*${hash_value}
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class HashedPassword {

    public static class Builder {
        private String salt;
        private int rounds;
        private final String method;

        public Builder(String method) {
            this.method = method;
        }

        public String getSalt() {
            return salt;
        }

        public Builder setSalt(String salt) {
            this.salt = salt;
            return this;
        }

        public int getRounds() {
            return rounds;
        }

        public Builder setRounds(int rounds) {
            this.rounds = rounds;
            return this;
        }

        public HashedPassword getHashInfo(String hashedValue) {
            return new HashedPassword(method, salt, rounds, hashedValue);
        }
    }

    /** method used to hash the password (e.g., md5, PBKDF2WithHmacSHA1) */
    private final String method;
    /** salt */
    private final String salt;
    /** # rounds */
    private final int rounds;
    /** actual hash result */
    private final String hashValue;

    public HashedPassword(String method, String salt, int rounds, String hashValue) {
        if (hashValue == null)
            throw new IllegalArgumentException("must specify hash value");

        this.method = method;
        this.salt = salt;
        this.rounds = rounds;
        this.hashValue = hashValue;
    }

    @Override
    public String toString() {
        return getHashedPassword();
    }

    /** serialize the HashedPasswordInfo into a string */
    public String getHashedPassword() {
        StringBuilder buf = new StringBuilder();

        if(!Strings.isNullOrEmpty(method))
            buf.append("method=").append(method).append(',');

        if (!Strings.isNullOrEmpty(salt))
            buf.append("salt=").append(salt).append(',');

        if (rounds > 0)
            buf.append("rounds=").append(rounds).append(',');

        buf.append(hashValue);
        return buf.toString();
    }

    public String getMethod() {
        return method;
    }

    public String getSalt() {
        return salt;
    }

    public int getRounds() {
        return rounds;
    }

    public String getHashValue() {
        return hashValue;
    }

    /** split the patterns by commas and removes surrounding whitespace */
    private final static Pattern commaPattern = Pattern.compile("\\s*,\\s*");

    /** matches assignments of type ${key}=${value}. Removes surrounding whitespace */
    private final static Pattern keyValuePattern = Pattern
            .compile("\\s*(\\w+)\\s*=\\s*([^\\s,]+)\\s*");

    /** parse a HashedPasswordInfo from a string.
     * @param hashString
     * @return
     * @throws IllegalArgumentException if hashString does not represent a valid HashedPasswordInfo
     */
    public static HashedPassword parse(String hashString) throws IllegalArgumentException {
        String method = null;
        String salt = null;
        int rounds = 0;
        String hashedPassword = null;

        if(hashString == null)
            throw new IllegalArgumentException("Hashed String may not be null");

        for (String s : commaPattern.split(hashString)) {
            if(s.indexOf('=') >= 0) {
                Matcher m = keyValuePattern.matcher(s);
                if (m.matches()) {
                    String key = m.group(1);
                    String val = m.group(2);
                    if ("method".equals(key)) {
                        method = val;
                    } else if ("salt".equals(key)) {
                        salt = val;
                    } else if ("rounds".equals(key)) {
                        try {
                            rounds = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Error parsing rounds: "
                                    + e.getMessage(), e);
                        }
                    } else {
                        throw new IllegalArgumentException(
                                "Error parsing password hash: key " + key + " unknown");
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Error parsing password hash stanza cannot be parsed "+s);
                }
            } else {
                hashedPassword = s;
            }

        }
        return new HashedPassword(method, salt, rounds, hashedPassword);
    }
}
