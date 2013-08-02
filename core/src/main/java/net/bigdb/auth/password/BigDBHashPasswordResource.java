package net.bigdb.auth.password;

import java.util.Collections;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBQuery;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Step;
import net.floodlightcontroller.bigdb.FloodlightResource;

/** Exposes the hash-password pseudo-collection to hash a password. Call with a query of
 *  /api/v1/data/controller/core/aaa/hash-password[password='foobar']

 *  The result looks like this
 *  {
 * "hashed-password" : "method=PBKDF2WithHmacSHA1,salt=5C-RsZoBdw7YH_LvCq9Wmw,rounds=10240,37ROndNx8M1MMhzOJyyxmGmrHYzNm4wxy933GAfmi4A"
 *  }
 *
 *  @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBHashPasswordResource extends FloodlightResource {
    private final PasswordHasher passwordHasher;

    /** shallow container representing the returned BigDB datastructure */
    public static class HashResult {

        // Currently we need to include the password in the HashResult so that
        // the HashResult doesn't get filtered out by the predicate filtering in
        // the BigDB core code. Eventually the BigDB core code should support
        // an annotation on a method that indicates that the method doesn't
        // need/want the BigDB core code to do the predicate filtering after
        // invoking the dynamic data hook.
        private final String password;
        private final String hashedPassword;

        public HashResult(String password, String hashedPassword) {
            this.password = password;
            this.hashedPassword = hashedPassword;
        }

        public String getPassword() {
            return password;
        }

        @BigDBProperty("hashed-password")
        public String getHashedPassword() {
            return hashedPassword;
        }
    }

    public BigDBHashPasswordResource(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    @BigDBQuery
    public List<HashResult> getHashedPasswordResult(
            @BigDBParam("location-path") LocationPathExpression locationPath) throws BigDBException {
        Step sessionStep = locationPath.getSteps().get(0);
        String password = (String) sessionStep.getExactMatchPredicateValue("password");
        if(password  == null)
            throw new BigDBException("Password to hash not specified");

        HashedPassword info = passwordHasher.hashPassword(password);
        HashResult hashResult = new HashResult(password, info.toString());
        return Collections.singletonList(hashResult);
    }
}
