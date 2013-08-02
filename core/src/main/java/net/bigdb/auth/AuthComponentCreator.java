package net.bigdb.auth;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** utility that creates an auth plugin component (e.g., a session manager, an authorizer, etc). Looks for and uses three different kind of constructors:
 *  <ol>
 *   <li><b> Constructors marked with the annotation CreateAuthComponent.</b><br>
 *      each parameter must be annotated with a CreateAuthParam annotation, whose value is the name of a AuthConfig parameter to be selected for the parameter
 *    Example:
 *    <pre>
 *    @CreateAuthComponent
 *    public FileSessionManager(
 *                 @CreateAuthParam("sessionDirectory") File sessionDir,
 *                 @CreateAuthParam("sessionCacheSpec") String cacheSpec) throws IOException {
 *            (...)
 *   }
 *   </pre>
 *   In the given example AuthComponent creator will create FileSessionManager using this constructor. For the parameter, it will substitute the authConfig
 *   Parameters of the given name. In over words, it will call the equivalent of:
 *   <pre>
 *        new FileSessionManager(authConfig.getParam("sessionDirectory"), authConfig.getParam("sessionCacheSpec"));
 *   </pre>
 *   <li><b> Constructors with a single AuthConfig parameter</b>
 *       These will be invokoked with the authConfig passed along
 *   <li><b> The empty Constructor()</b>
 *   </ol>
 *
 *   Exceptions thrown by the constructors are converted into IllegalArgumentExceptions
 *
 * @see CreateAuthComponent
 * @see CreateAuthParam
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
class AuthComponentCreator<T> {
    protected static Logger logger =
            LoggerFactory.getLogger(AuthComponentCreator.class);

    private final Class<T> clazz;

    AuthComponentCreator(Class<T> clazz) {
        this.clazz = clazz;
    }

    /** convenience method. Create an instance of class clazz, using authConfig authConfig, and the ruleset specified above */
    public static <T> T create(Class<T> clazz, AuthConfig authConfig) {
        return AuthComponentCreator.forClass(clazz).instantiate(authConfig);
    }

    public static <T> AuthComponentCreator<T> forClass(Class<T> clazz) {
        return new AuthComponentCreator<T>(clazz);
    }

    public T instantiate(AuthConfig authConfig) {
        try {
            Constructor<T> authCreator = findAuthCreatorConstructor();
            if(authCreator != null) {
                Object[] authCreatorParams = getAuthCreatorParams(authCreator, authConfig);
                return authCreator.newInstance(authCreatorParams);
            } else {
                if(logger.isDebugEnabled())
                    logger.debug("Auth component: "+clazz +": No constructor with annotation CreateAuthComponent found");
            }

            try {
                Constructor<T> c = clazz.getConstructor(AuthConfig.class);
                return c.newInstance(authConfig);
            } catch(NoSuchMethodException e) {
                if(logger.isDebugEnabled())
                    logger.debug("Auth component: "+clazz +": No constructor with parameter authConfig found - trying empty constructor");
            }
            return clazz.newInstance();
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Could not instantiate "+clazz, e.getCause());
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Could not instantiate default authorizer "+clazz, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Could not instantiate default authorizer "+clazz, e);
        }
    }

    private Object[] getAuthCreatorParams(Constructor<T> authCreator, AuthConfig authConfig) {
        Class<?>[] parameterTypes = authCreator.getParameterTypes();
        int len = parameterTypes.length;
        Object[] res = new Object[len];
        Annotation[][] paramAnnotations = authCreator.getParameterAnnotations();
        for(int i=0; i < len; i++) {
            Annotation[] annotations = paramAnnotations[i];
            String paramKey = null;
            for(int j=0; j < annotations.length; j++) {
                Annotation annotation = annotations[j];
                if(annotation instanceof CreateAuthParam) {
                    paramKey = ((CreateAuthParam) annotation).value();
                    break;
                }
            }
            if(paramKey == null)
                throw new IllegalStateException("Parameter "+i + " of constructor "+ authCreator + " of type "+clazz + " does not have AuthParam annnotation");

            Object paramValue = authConfig.getParam(paramKey);
            if(! parameterTypes[i].isAssignableFrom(paramValue.getClass())) {
                    throw new IllegalStateException(
                            "Parameter "+i + " of constructor "+ authCreator + " of type "+clazz +
                            ": Type mismatch: Expected "+parameterTypes[i] + " / found "+ paramValue.getClass());

            }
            res[i] = paramValue;
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    private Constructor<T> findAuthCreatorConstructor() {
        for(Constructor<?> c: clazz.getConstructors()) {
            if(c.isAnnotationPresent(CreateAuthComponent.class)) {
                return (Constructor<T>) c;
            }
        }
        return null;
    }
}
