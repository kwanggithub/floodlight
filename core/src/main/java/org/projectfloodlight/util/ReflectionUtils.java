package org.projectfloodlight.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** static helper methods to deal with reflection.
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public final class ReflectionUtils {
    protected static final Logger logger = 
            LoggerFactory.getLogger(ReflectionUtils.class);

    // don't instantiate
    private ReflectionUtils() {}

    /** return null if object is null or the object's classes toString otherwise */
    public static final String getClassName(Object o) {
       if(o==null)
           return "null";
       else
           return o.getClass().toString();
    }

    /** convenience method. Converts all checked exceptions into IllegalArgumentExceptions. Unchecked
     *  exceptions from the constructor body are rethrown unmodified
     *
     * @param targetClass
     * @param parameters
     * @return
     * @throws IllegalArgumentException
     */
    public static <T> T createUnchecked(Class<T> targetClass, Object... parameters) throws IllegalArgumentException{
        try {
            return create(targetClass, parameters);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Constructor Parameter type mismatch - shouldn't happen ", e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot instantiate class "+ targetClass +" - abstract/interface? ", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot acess class constructor of class " + targetClass + " - not public? ", e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if(t instanceof RuntimeException)
                throw ((RuntimeException ) t);
            else
                throw new IllegalArgumentException("Exception thrown during constructor invocation of class "+targetClass +": ", t);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Could not find constructor",e);
        }
    }

    /** create an instance of targetClass, using a constructor that fits the given parameters.
     *
     * @param targetClass
     * @param parameters
     * @throws IllegalArgumentException if no matching constructor is found or an exception occurs during
     *         its invocation.
     *         <strong>Note:</strong> A RuntimeException thrown by the constructor will directly be retrhown
     *         and not wrapped. All other exceptions are (necessarily) wrapped.
     * @return the newly created instance
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> targetClass, Object... parameters) throws IllegalArgumentException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<T> c = null;
        Constructor<?>[] candidates = targetClass.getConstructors();
        candSearch: for(Constructor<?> cand : candidates) {
            Class<?>[] candParamTypes = cand.getParameterTypes();
            logger.debug("Cand: " + cand);

            if(candParamTypes.length != parameters.length)
                continue;
            for(int i=0; i<parameters.length; i++) {
                Class<?> paramClass = candParamTypes[i];
                if(paramClass.isPrimitive()) {
                    if(!canConvertPrimitive(paramClass, parameters[i]))
                        break candSearch;
                } else if(!paramClass.isAssignableFrom(parameters[i].getClass())) {
                    break candSearch;
                }
            }
            // all parameters match up
            c = (Constructor<T>) cand;
        }
        if(c == null) {
            throw new NoSuchMethodException("No public constructor found that matches parameters " + Arrays.toString(parameters));
        } else {
            return c.newInstance(parameters);
        }
    }

    private static boolean canConvertPrimitive(Class<?> primitiveClass, Object object) {
        if(primitiveClass == Boolean.TYPE) {
            return object instanceof Boolean;
        } else if (primitiveClass == Byte.TYPE) {
            return object instanceof Byte;
        } else if (primitiveClass == Short.TYPE) {
            return object instanceof Short || object instanceof Byte;
        } else if (primitiveClass == Integer.TYPE) {
            return object instanceof Integer || object instanceof Short || object instanceof Byte;
        } else if (primitiveClass == Long.TYPE) {
            return object instanceof Long || object instanceof Integer || object instanceof Short || object instanceof Byte;
        } else {
            logger.warn("Unknown primitive type class"+primitiveClass);
            return false;
        }
    }

}
