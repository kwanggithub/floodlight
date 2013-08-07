package org.projectfloodlight.db.auth.password;

/** Interface contract for a password hashing function. Hashes a clearPassword to a HashedPasswordInfo, and
 *  can check a clearPassword against said info.
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface PasswordHasher {
    /** the name of the password hashing function */
    String getName();

    /** hash the password using a one-way-function.
     *
     * @param clearPassword
     * @return hashedPassword
     */
    HashedPassword hashPassword(String clearPassword);

    /** check the password against the stored HashedPassword
     *
     * @param clearPassword
     * @param hashed
     * @return
     */
    boolean checkPassword(String clearPassword, HashedPassword hashed);
}