package org.projectfloodlight.db.auth.password;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.projectfloodlight.db.auth.password.HashedPassword;
import org.projectfloodlight.db.auth.password.PasswordHasherMd5;

public class PasswordHasherMd5Test {
    @Test
    public void testHashedPassword() {
        PasswordHasherMd5 h = new PasswordHasherMd5();
        HashedPassword info = h.hashPassword("test");
        assertEquals("098f6bcd4621d373cade4e832627b4f6", info.getHashValue());
        assertNull(info.getSalt());
    }

    @Test
    public void testCheckPassword() {
        PasswordHasherMd5 h = new PasswordHasherMd5();
        HashedPassword info = new HashedPassword.Builder("md5").getHashInfo("098f6bcd4621d373cade4e832627b4f6");
        assertTrue(h.checkPassword("test", info));
        assertFalse(h.checkPassword("test2", info));
    }

}
