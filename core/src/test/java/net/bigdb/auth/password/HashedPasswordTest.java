package net.bigdb.auth.password;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HashedPasswordTest {
    @Test
    public void testBuild() {
        HashedPassword.Builder b = new HashedPassword.Builder("foo");
        b.setRounds(4);
        b.setSalt("saltier");
        HashedPassword h = b.getHashInfo("hashedSnacks");

        assertEquals(4, b.getRounds());
        assertEquals("saltier", h.getSalt());
        assertEquals("hashedSnacks", h.getHashValue());

        String s = h.toString();
        assertEquals("method=foo,salt=saltier,rounds=4,hashedSnacks", s);
    }


    @Test
    public void testParseFull() {
        HashedPassword h = HashedPassword.parse("method=foo,salt=abc,rounds=5,hashedPassword");
        assertEquals("foo", h.getMethod());
        assertEquals("abc", h.getSalt());
        assertEquals(5, h.getRounds());
        assertEquals("hashedPassword", h.getHashValue());
    }

    @Test
    public void testParseSimple() {
        HashedPassword h = HashedPassword.parse("method=md5,098f6bcd4621d373cade4e832627b4f6");

        assertEquals("md5", h.getMethod());
        assertNull(h.getSalt());
        assertEquals(0, h.getRounds());
        assertEquals("098f6bcd4621d373cade4e832627b4f6", h.getHashValue());
    }

    @Test
    public void testParseWithSalt() {
        HashedPassword h = HashedPassword.parse("method=md5,salt=foo,098f6bcd4621d373cade4e832627b4f6");

        assertEquals("md5", h.getMethod());
        assertEquals("foo", h.getSalt());
        assertEquals(0, h.getRounds());
        assertEquals("098f6bcd4621d373cade4e832627b4f6", h.getHashValue());
    }

    @Test
    public void testParsePBKDF() {
        HashedPassword h = HashedPassword.parse("method=PBKDF2WithHmacSHA1,salt=jQK5eP524r_mjRMxP3AYjg,rounds=10240,6JkFgk-wijPZsP_eSVtj36torN0DDOHCL121pfcS-Lc");
        assertEquals("PBKDF2WithHmacSHA1", h.getMethod());
        assertEquals("jQK5eP524r_mjRMxP3AYjg", h.getSalt());
        assertEquals(10240, h.getRounds());
        assertEquals("6JkFgk-wijPZsP_eSVtj36torN0DDOHCL121pfcS-Lc", h.getHashValue());
    }


    @Test(expected=IllegalArgumentException.class)
    public void testInvalid1() {
        HashedPassword.parse("method=foo,=abc,rounds=5,hashedPassword");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalid2() {
        HashedPassword.parse("method=foo,saltx=abc,rounds=5,hashedPassword");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalid3() {
        HashedPassword.parse("method=foo,salt=abc,");
    }


}
