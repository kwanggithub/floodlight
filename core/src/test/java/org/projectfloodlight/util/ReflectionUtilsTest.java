package org.projectfloodlight.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;

import org.junit.Test;
import org.projectfloodlight.util.ReflectionUtils;

public class ReflectionUtilsTest {
    public static class A {
        int ctorUsed = 0;
        private String param31;
        private int param32;
        private String param2;

        A(Object o) {
            // shouldn't be invoked because it's not public
            ctorUsed = 4;
        }

        public A() {
            ctorUsed = 1;
        }

        public A(String param2) {
            this.param2 = param2;
            ctorUsed = 2;
        }

        public A(String param31, int param32) {
            this.param31 = param31;
            this.param32 = param32;
            ctorUsed = 3;
        }
    }

    public static class B {
        private static RuntimeException exception;

        public B() {
            exception = new RuntimeException();
            throw exception;
        }
    }

    @Test
    public void testCreate() throws Exception {
        A a1 = ReflectionUtils.create(A.class);
        assertEquals(1, a1.ctorUsed);
        A a2 = ReflectionUtils.create(A.class, "hallo");
        assertEquals(2, a2.ctorUsed);
        assertEquals("hallo", a2.param2);
        A a3 = ReflectionUtils.create(A.class, "hallo", 12);
        assertEquals(3, a3.ctorUsed);
        assertEquals("hallo", a3.param31);
        assertEquals(12, a3.param32);
        A a4 = ReflectionUtils.create(A.class, "hallo", (byte) 123);
        assertEquals(123, a4.param32);
        A a5 = ReflectionUtils.create(A.class, "hallo", (short) 123);
        assertEquals(123, a5.param32);
        A a6 = ReflectionUtils.create(A.class, "hallo", 123);
        assertEquals(123, a6.param32);
    }

    @Test(expected=NoSuchMethodException.class)
    public void testCreateNoConstructorFound() throws Exception {
        ReflectionUtils.create(A.class, 123);
    }

    @Test(expected=NoSuchMethodException.class)
    public void testCreateNoConstructorFound2() throws Exception {
        ReflectionUtils.create(A.class, new Object());
    }

    @Test(expected=InvocationTargetException.class)
    public void testConstructorThrowsException() throws Exception {
        ReflectionUtils.create(B.class);
    }

    @Test
    public void testUncheckedConstructorThrowsException() throws Exception {
        try {
            ReflectionUtils.createUnchecked(B.class);
            fail("Exception expected");
        } catch(RuntimeException e) {
            assertEquals(B.exception, e);
        }
    }
}
