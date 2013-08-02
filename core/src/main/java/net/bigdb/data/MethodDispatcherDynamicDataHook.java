package net.bigdb.data;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import net.bigdb.BigDBException;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Query;
import net.bigdb.util.Path;
import net.floodlightcontroller.util.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a dynamic data hook implementation that dispatches to a method
 * in a POJO dynamic data resource class that was annotated to indicate that
 * it services one or more BigDB operations. This works in conjunction with the
 * ServerDataSource class to (mostly) provide backwards compatibility with the
 * older BigDB dynamic data mechanism. Application code registers POJO resource
 * classes or objects with the server data source. Methods in the resource class
 * are annotated with @BigDBQuery, @BigDBReplace, @BigDBUpdate, or @BigDBDelete
 * annotations to indicate that they are the methods to be invoked for those
 * operations. The methods can also have @BigDBPath annotations to indicate
 * the path in the schema tree (relative to the node where the resource was
 * registered) for which they should be invoked. Each argument in the method
 * must have a @BigDBParam annotation to indicate the source/type of that
 * argument.
 *
 * The BigDBParam names that are currently supported are:
 *  - "location-path": the location path of the requested operation, adjusted to
 *                     be relative to the specified base path.
 *  - "query": the query from the top-level request. Currently the paths in the
 *             query are not adjusted to be relative to the base path.
 *  - "operation": the operation type of the current request. This is useful
 *                 for the (probably rare) case where a resource method
 *                 services multiple operation types.
 *  - "mutation-data": the data node representing the input data for a replace or
 *                     update operation.
 *  - "auth-context": the authentication context for the current request
 *  - "context": the context for the underlying dynamic data hook. This ensures
 *               that the method can always access all of the available
 *               information exposed in the lower-level mechanism.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class MethodDispatcherDynamicDataHook implements DynamicDataHook {

    protected final static Logger logger =
            LoggerFactory.getLogger(MethodDispatcherDynamicDataHook.class);

    /**
     * In the old API resources could be attached relative to another node in
     * the tree and the location paths that were passed to the target method
     * were adjusted to be relative to the base node. This data member keeps
     * track of the base node path, so that that adjustment can be made.
     */
    private final Path basePath;

    /** The target method to be invoked */
    private final Method targetMethod;

    /** The target object to be used when invoking the target method */
    private final Object targetObject;

    public MethodDispatcherDynamicDataHook(Path basePath, Method targetMethod,
            Object targetObject) {
        this.basePath = basePath;
        this.targetMethod = targetMethod;
        this.targetObject = targetObject;
    }

    @Override
    public Object doDynamicData(Context context) throws BigDBException {

        // Get the absolute path where the dynamic data hook is registered.
        LocationPathExpression locationPath = context.getHookPath();

        // Adjust the location path to make it relative to the base path
        int basePathSize = basePath.size();
        if (basePathSize > 0)
            locationPath = locationPath.subpath(basePathSize, locationPath.size());

        // Get the target object. This may be the targetObject that was
        // specified when the hook was registered. Or if the hook was registered
        // by class and the method is not static then a new instance is
        // created (this should be rare).
        // FIXME: Is there a valid use case for creating the instance dynamically?
        Object object = null;
        Class<?> targetClass = targetMethod.getDeclaringClass();
        boolean isStaticMethod = Modifier.isStatic(targetMethod.getModifiers());
        if (!isStaticMethod) {
            object = targetObject;
            if (object == null) {
                try {
                    // The target class must have a default (empty) constructor
                    Constructor<?> constructor = targetClass.getConstructor();
                    object = constructor.newInstance();
                }
                catch (NoSuchMethodException e) {
                    throw new BigDBException(
                            "Target method class must have empty constructor", e);
                }
                catch (Exception e) {
                    throw new BigDBException(
                            "Error instantiating target method instance", e);
                }
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Target method: " + targetMethod.toString());
        }

        // Collect the arguments to the target method
        Annotation[][] parameterAnnotations =
                targetMethod.getParameterAnnotations();
        Object[] methodArguments = new Object[parameterAnnotations.length];

        // Iterate over the parameter types checking for the param annotation
        // and map the string values parsed from the path to the appropriate
        // argument.
        for (int i = 0; i < parameterAnnotations.length; i++) {
            String parameterName = null;
            for (Annotation annotation: parameterAnnotations[i]) {
                if (annotation instanceof BigDBParam) {
                    parameterName = ((BigDBParam)annotation).value();
                    break;
                }
            }
            if (parameterName == null) {
                throw new BigDBException("Request router method " +
                        "parameter name not specified");
            }
            Object methodArgument;
            // These are the supported parameters that can be passed to the
            // resource method. See the class-level JavaDoc comments for more
            // information about what these correspond to.
            if (parameterName.equals("location-path")) {
                methodArgument = locationPath;
            } else if (parameterName.equals("query")) {
                methodArgument = Query.of(locationPath);
            } else if (parameterName.equals("operation")) {
                methodArgument = context.getOperation();
            } else if (parameterName.equals("mutation-data")) {
                methodArgument = context.getMutationDataNode();
            } else if (parameterName.equals("auth-context")) {
                methodArgument = context.getAuthContext();
            } else if (parameterName.equals("context")) {
                methodArgument = context;
            } else {
                throw new BigDBException("Invalid BigDBParam name");
            }
            methodArguments[i] = methodArgument;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Target method parameters:\n" +
                    Arrays.toString(methodArguments));
        }

        // Invoke the target method
        Object outputObject = null;
        try {
            logger.debug("Invoking target method");
            outputObject = targetMethod.invoke(object, methodArguments);
        }
        catch (InvocationTargetException exc) {
            throw new BigDBException("Dynamic data handler threw an exception",
                    ExceptionUtils.unwrapOrThrow(exc, BigDBException.class));
        } catch (Exception exc) {
            throw new BigDBException("Error invoking dynamic data handler",
                    ExceptionUtils.unwrapOrThrow(exc, BigDBException.class));
        }

        return outputObject;
    }

    @Override
    public String toString() {
        return "MethodDispatcherDynamicDataHook [basePath=" + basePath + ", targetMethod="
                + targetMethod + ", targetObject=" + targetObject + "]";
    }

}
