package org.projectfloodlight.db.data;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.annotation.BigDBDelete;
import org.projectfloodlight.db.data.annotation.BigDBInsert;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.data.annotation.BigDBReplace;
import org.projectfloodlight.db.data.annotation.BigDBUpdate;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data source implementation that supports registering POJO dynamic data
 * resource classes with annotated methods to indicate that they service
 * operational state in the BigDB tree. This class works in conjunction with
 * the MethodDispatcherDynamicDataHook class to support this functionality.
 * This implementation is mainly intended as a backward compatibility
 * implementation for the older BigDB dynamic data mechanism. New code should
 * use the underlying DynamicDataHook mechanism directly since we may
 * eventually deprecate/remove this functionality.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class ServerDataSource extends DynamicDataSource {

    protected final static Logger logger =
            LoggerFactory.getLogger(ServerDataSource.class);

    public ServerDataSource(String name, Schema schema) throws BigDBException {
        super(name, false, schema);
        registerDynamicDataHooksFromObject(Path.ROOT_PATH, this);
    }

    /**
     * Register the target resource object at the specified path in the tree.
     * Resource methods are looked up via reflection in the class of the
     * specified target object.
     *
     * @param basePath the base path for the registered resource object. The
     * "location-path" BigDBParam argument to the resource method is adjusted
     * to be relative to this base path
     * @param targetObject the target object used to service dynamic data hook
     * request. DynamicDataHook adapter objects are created for any methods
     * in the class of the target object that are have @BigDBQuery,
     * @BigDBReplace, @BigDBUpdate, or @BigDBDelete annotations. The target
     * object is the instance that's used when invoking any of the target
     * methods that are found via reflection.
     *
     * @throws BigDBException
     */
    public void registerDynamicDataHooksFromObject(Path basePath,
            Object targetObject) throws BigDBException {
        registerDynamicDataHooksFromObject(basePath, Path.EMPTY_PATH,
                targetObject);
    }

    /**
     * Register the target resource object at the specified path in the tree.
     * Resource methods are looked up via reflection in the class of the
     * specified target object.
     *
     * @param basePath the base path for the registered resource object. The
     * "location-path" BigDBParam argument to the resource method is adjusted
     * to be relative to this base path
     * @param relativePath additional relative path that is included in the
     * registration path. This path is combined with the @BigDBPath annotations
     * on the resource class and/or target method to form the complete
     * registration path
     * @param targetObject the target object used to service dynamic data hook
     * requests. DynamicDataHook adapter objects are created for any methods
     * in the class of the target object that are have @BigDBQuery,
     * @BigDBReplace, @BigDBUpdate, or @BigDBDelete annotations. The target
     * object is the instance object that's used when invoking any of the
     * target methods that are found via reflection.
     *
     * @throws BigDBException
     */
    public void registerDynamicDataHooksFromObject(Path basePath,
            Path relativePath, Object targetObject) throws BigDBException {
        registerDynamicDataHooks(basePath, relativePath, targetObject
                .getClass(), targetObject);
    }

    /**
     * Register the target resource class at the specified path in the tree.
     * Resource methods are looked up via reflection in the target class. If
     * the resource method is static then null is passed for the instance
     * when the method is invoked. If the method is not static then a new
     * instance is constructed (using the default constructor) each time the
     * method is invoked.
     *
     * @param basePath the base path for the registered resource class. The
     * "location-path" BigDBParam argument to the resource method is adjusted
     * to be relative to this base path. The base path is combined with any
     * @BigDBPath annotations on the resource class or target methods to
     * determine the complete hook registration path.
     * @param relativePath additional relative path that is included in the
     * registration path. This path is combined with the @BigDBPath annotations
     * on the resource class and/or target method to form the complete
     * registration path
     * @param targetClass the target class used to service dynamic data hook
     * requests. DynamicDataHook adapter objects are created for any methods
     * in the class of the target object that are have @BigDBQuery,
     * @BigDBReplace, @BigDBUpdate, or @BigDBDelete annotations.
     *
     * @throws BigDBException
     */
    public void registerDynamicDataHooksFromClass(Path basePath,
            Class<?> targetClass) throws BigDBException {
        registerDynamicDataHooksFromClass(basePath, Path.EMPTY_PATH,
                targetClass);
    }

    /**
     * Register the target resource class at the specified path in the tree.
     * Resource methods are looked up via reflection in the target class. If
     * the resource method is static then null is passed for the instance
     * when the method is invoked. If the method is not static then a new
     * instance is constructed (using the default constructor) each time the
     * method is invoked.
     *
     * @param basePath the base path for the registered resource class. The
     * "location-path" BigDBParam argument to the resource method is adjusted
     * to be relative to this base path. The base path is combined with any
     * @BigDBPath annotations on the resource class or target methods to
     * determine the complete hook registration path.
     * @param relativePath additional relative path that is included in the
     * registration path. This path is combined with the @BigDBPath annotations
     * on the resource class and/or target method to form the complete
     * registration path
     * @param targetClass the target class used to service dynamic data hook
     * requests. DynamicDataHook adapter objects are created for any methods
     * in the class of the target object that are have @BigDBQuery,
     * @BigDBReplace, @BigDBUpdate, or @BigDBDelete annotations.
     *
     * @throws BigDBException
     */
    public void registerDynamicDataHooksFromClass(Path basePath,
            Path relativePath, Class<?> targetClass) throws BigDBException {
        registerDynamicDataHooks(basePath, relativePath, targetClass, null);
    }

    /**
     * Internal method used to support all of the public methods for
     * registering resource classes and objects.
     *
     * @param basePath the base path for the registered resource class. The
     * "location-path" BigDBParam argument to the resource method is adjusted
     * to be relative to this base path. The base path is combined with any
     * @BigDBPath annotations on the resource class or target methods to
     * determine the complete hook registration path.
     * @param relativePath additional relative path that is included in the
     * registration path. This path is combined with the @BigDBPath annotations
     * on the resource class and/or target method to form the complete
     * registration path. This is Path.EMPTY_PATH if the resource was
     * registered using one of the methods that doesn't have a relative path
     * argument.
     * @param targetClass the target class used to service dynamic data hook
     * requests. DynamicDataHook adapter objects are created for any methods
     * in this class that have @BigDBQuery, @BigDBReplace, @BigDBUpdate, or
     * @BigDBDelete annotations.
     * @param targetObject the target object used to service dynamic data hook
     * requests. This is null if the resource was registered by class
     * @throws BigDBException
     */
    private void registerDynamicDataHooks(Path basePath, Path relativePath,
            Class<?> targetClass, Object targetObject) throws BigDBException {

        Path classPath = relativePath;

        // Check if there's a path annotation on the target class
        BigDBPath classPathAnnotation =
                targetClass.getAnnotation(BigDBPath.class);
        if (classPathAnnotation != null) {
            String classPathString = classPathAnnotation.value();
            classPath = classPath.getChildPath(classPathString);
        }

        // Search through the methods in the target class for target methods
        for (Method method: targetClass.getMethods()) {

            // Check for @BigDB* annotations and add them to the set of
            // operations for which this hook is enabled.
            Set<DynamicDataHook.Operation> operations =
                    EnumSet.noneOf(DynamicDataHook.Operation.class);
            if (method.isAnnotationPresent(BigDBQuery.class)) {
                operations.add(DynamicDataHook.Operation.QUERY);
            }
            if (method.isAnnotationPresent(BigDBInsert.class)) {
                operations.add(DynamicDataHook.Operation.INSERT);
            }
            if (method.isAnnotationPresent(BigDBReplace.class)) {
                operations.add(DynamicDataHook.Operation.REPLACE);
            }
            if (method.isAnnotationPresent(BigDBUpdate.class)) {
                operations.add(DynamicDataHook.Operation.UPDATE);
            }
            if (method.isAnnotationPresent(BigDBDelete.class)) {
                operations.add(DynamicDataHook.Operation.DELETE);
            }

            if (operations.isEmpty())
                continue;

            Path methodPath = classPath;

            // Check if the method has a path annotation
            BigDBPath methodPathAnnotation =
                    method.getAnnotation(BigDBPath.class);
            if (methodPathAnnotation != null) {
                methodPath =
                        methodPath.getChildPath(methodPathAnnotation.value());
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Registering dynamic data hook: "
                        + "base=\"{}\"; path=\"{}\"; ops={}, method={}",
                        new Object[] { basePath, methodPath, operations,
                                method });
            }

            // Create a dynamic data hook dispatcher object for the method
            DynamicDataHook dynamicDataHook =
                    new MethodDispatcherDynamicDataHook(basePath, method,
                            targetObject);
            Path registrationPath = basePath.getChildPath(methodPath);
            registerDynamicDataHook(registrationPath, dynamicDataHook,
                    operations);
        }
    }
}
