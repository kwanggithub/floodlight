package org.projectfloodlight.db.auth.password;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.projectfloodlight.db.auth.password.HashedPassword;
import org.projectfloodlight.db.auth.password.PasswordHasherPBKDF2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class PasswordHasherPBKDF2Test {
    private final static Logger logger =
            LoggerFactory.getLogger(PasswordHasherPBKDF2Test.class);
    @Test
    public void testHashedPassword() {
        PasswordHasherPBKDF2 h = new PasswordHasherPBKDF2();
        HashedPassword info = h.hashPassword("test");
        HashedPassword info2 = h.hashPassword("test");

        assertFalse("salts should be different", info.getSalt().equals(info2.getSalt()));
        assertFalse("hashed password should be different", info.getHashValue().equals(info2.getHashValue()));
        assertTrue(h.checkPassword("test", info));
        assertTrue(h.checkPassword("test", info2));
        assertTrue(info.getSalt().length() > 10);
        assertTrue(info.getRounds() > 100);
        assertTrue(info.getRounds() > 100);
        logger.info("Hash: "+info);

        HashedPassword reparsed = HashedPassword.parse(info.toString());
        assertTrue(h.checkPassword("test", reparsed));
        assertFalse(h.checkPassword("test ", reparsed));
        assertFalse(h.checkPassword("test1", reparsed));
    }

    @Test
    public void testCheckPassword() {
        PasswordHasherPBKDF2 h = new PasswordHasherPBKDF2();

        String pwEntry = "method=PBKDF2WithHmacSHA1,salt=hO8kJCzB_YdjGWfI9VCxQA,rounds=20480,v6pMzScpAs47FDgV1zjg5VbdDKA8QqT2cr96w25401I";
        Stopwatch s = new Stopwatch().start();
        assertTrue(h.checkPassword("test", HashedPassword.parse(pwEntry)));
        s.stop();

        // a 20000 rounds PBKDF should take a bit of time (that's the whole point)
        // if your computer is much faster than mine this may have to be adapted
        assertTrue("Should have taken longer than 30 ms (but took"+s.elapsed(TimeUnit.MILLISECONDS) +")", s.elapsed(TimeUnit.MILLISECONDS) > 30);
    }

}
