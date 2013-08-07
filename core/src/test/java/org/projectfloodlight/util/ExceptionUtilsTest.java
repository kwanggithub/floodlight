package org.projectfloodlight.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;

import org.junit.Test;
import org.projectfloodlight.util.ExceptionUtils;

public class ExceptionUtilsTest {
    static class SafeException extends Exception {
        private static final long serialVersionUID = 1L;
    }
    static class MyOtherException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testUnwrapOrRethrow() throws SafeException {
        //Unwrap or rethrow should rethrow RuntimeException, Error, MyException (safe class)
        testRethrown(new RuntimeException());
        testRethrown(new SafeException());
        testRethrown(new Error());

        // It shouldn't rethrow MyOtherException
        testReturned(new MyOtherException());

        testRethrownAndUnwrapped(new InvocationTargetException(new RuntimeException()));
        testRethrownAndUnwrapped(new InvocationTargetException(new SafeException()));
        testRethrownAndUnwrapped(new InvocationTargetException(new Error()));

        testReturnedAndUnwrapped(new InvocationTargetException(new MyOtherException()));
    }

    private void testReturned(Throwable thrownException) throws SafeException {
        Throwable returned = ExceptionUtils.unwrapOrThrow(thrownException, SafeException.class);
        assertEquals(thrownException, returned);
    }

    private void testReturnedAndUnwrapped(InvocationTargetException thrownException) throws SafeException {
        Throwable returned = ExceptionUtils.unwrapOrThrow(thrownException, SafeException.class);
        assertEquals(thrownException.getTargetException(), returned);
    }

    private <T extends Throwable, J extends Exception> void testRethrown(T thrownException) {
        try {
            ExceptionUtils.unwrapOrThrow(thrownException, SafeException.class);
            fail("Execption failed to be rethrown");
        }  catch(AssertionError e) {
            throw e;
        } catch(Throwable caught) {
            assertEquals(caught, thrownException);
        }
    }

    private <T extends Throwable, J extends Exception> void testRethrownAndUnwrapped(InvocationTargetException thrownException) {
        try {
            ExceptionUtils.unwrapOrThrow(thrownException, SafeException.class);
            fail("Execption failed to be rethrown");
        }  catch(AssertionError e) {
            throw e;
        } catch(Throwable caught) {
            assertEquals(thrownException.getTargetException(), caught);
        }
    }

}
