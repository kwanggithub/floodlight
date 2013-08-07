package org.projectfloodlight.util;

import java.lang.reflect.InvocationTargetException;

/** static helper methods for dealing with exceptions.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public final class ExceptionUtils {
    private ExceptionUtils() {}

    /** Helper method that supports the unwrapping of InvocationTargetExceptions and direct rethrowing of
     *  'allowed' exceptions.
     *  <p>
     *  Algorithm:
     *  <ol>
     *   <li>the Exception is unwrapped, i.e., if the exception is an InvocationTargetException, the 'real' targetException
     *  is pulled out.
     *   <li>if  Exception is a RuntimeException, a java.lang.Error, or an instance of the given 'allowed' exception class, it will be rethrown.
     *   <li> otherwise, it is returned as the cuase can construct a custom exception[*]
     *  </ol>
     *  <p>
     *  Example use:
     *  <code>
     *   catch(Exception e) {
     *       throw new BigDBException("Error in operation: ",
     *           ReflectionUtil.unwrapOrThrow(e, BigDBException));
     *   }
     *   </code>
     *
     *  <hr>
     *  [*] This method does not construct new Exceptions. The reason is that we want the stackTrace of the exception to
     *      point to the client code, not this utility method.
     *
     * @param t the exception to be unwrapped, and potentially rethrown
     * @param safeExceptionClass a 'safe' class of exceptions that should be directly rethrown
     * @return if the unwrapped exception is an isntance of safeExceptionClass or RuntimeException, rethrows the exception
     *     and does <strong>not</strong> return. Otherwise, returns the unwrappedException to the caller.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Exception> Throwable unwrapOrThrow(Throwable t, Class<T> safeExceptionClass) throws T {
        Throwable target;
        if(t instanceof InvocationTargetException) {
            target = ((InvocationTargetException) t).getTargetException();
        } else {
            target = t;
        }
        if(target instanceof RuntimeException)
            throw (RuntimeException) target;
        if(target instanceof Error)
            throw (Error) target;

        if (safeExceptionClass != null) {
            if(safeExceptionClass.isInstance(target))
                throw (T) target;
        }
        return target;
    }
}
