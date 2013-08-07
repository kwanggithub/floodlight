package org.projectfloodlight.db.auth.password;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** the system default password hasher. Has a map of supported passwordhashers (that are used for
 *  checking stored password) and a default passwordhasher that is used for hashing new passwords.
 *
 *  Currently hardcoded to use PBKDF2 as a default hasher, and support unsalted MD5 if you really dare.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class MultiPasswordHasher implements PasswordHasher {
    private static final String NAME = "multi";
    private PasswordHasher defaultHasher;
    private final ConcurrentMap<String, PasswordHasher> hashers = new ConcurrentHashMap<String, PasswordHasher>();
    private final PasswordHasherMd5 md5Hasher;

    public MultiPasswordHasher() {

        PasswordHasher pbkdf2 = new PasswordHasherPBKDF2();
        addDefaultHasher(pbkdf2);

        md5Hasher = new PasswordHasherMd5();
        addHasher(md5Hasher);
    }

    public void addDefaultHasher(PasswordHasher hasher) {
        addHasher(hasher);
        this.defaultHasher = hasher;
    }

    private void addHasher(PasswordHasher hasher) {
        PasswordHasher present = hashers.putIfAbsent(hasher.getName(), hasher);
        if(present != null) {
            throw new IllegalStateException("Hasher with name "+hasher.getName() + " already present");
        }
    }

    @Override
    public HashedPassword hashPassword(String clearPassword) {
        return defaultHasher.hashPassword(clearPassword);
    }

    @Override
    public boolean checkPassword(String clearPassword, HashedPassword info) {
        PasswordHasher hasher;
        if(info.getMethod() != null) {
            hasher = hashers.get(info.getMethod());
            if(hasher == null)
                throw new IllegalArgumentException("Hasher "+info.getMethod()+ " not known");
        } else {
            hasher = defaultHasher;
        }
        return hasher.checkPassword(clearPassword, info);
    }

    @Override
    public String getName() {
       return NAME;
    }

}
