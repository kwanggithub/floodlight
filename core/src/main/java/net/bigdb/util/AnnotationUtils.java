package net.bigdb.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class AnnotationUtils {

    private AnnotationUtils() {
    }

    public static <A extends Annotation> A findMethodAnnotation(
            Method method, Class<A> annType) {
        A annotation = method.getAnnotation(annType);
        if (annotation != null)
            return annotation;

        Class<?> declaringClass = method.getDeclaringClass();

        Class<?> superclass = declaringClass.getSuperclass();
        while (superclass != null) {
            try {
                Method m = superclass.getMethod(method.getName(),
                        method.getParameterTypes());
                annotation = m.getAnnotation(annType);
                if (annotation != null)
                    return annotation;
            } catch (NoSuchMethodException e) {
                // Just ignore
            }
            superclass = superclass.getSuperclass();
        }

        // look at the interface
        // we are programmatic here to only go one step further
        // this is most likely enough for now.
        Class<?>[] interfaces = declaringClass.getInterfaces();
        for (Class<?> ifc : interfaces) {
            try {
                Method m = ifc.getMethod(method.getName(),
                        method.getParameterTypes());
                annotation = m.getAnnotation(annType);
            } catch (NoSuchMethodException e) {
                // Just ignore
            }
        }

        return annotation;
    }

    public static <A extends Annotation> A findClassAnnotation(
            Class<?> objType, Class<A> annType) {
        A annotation = objType.getAnnotation(annType);
        if (annotation != null) {
            // we are done
            return annotation;
        }
        Class<?> superclass = objType.getSuperclass();
        if (superclass != null) {
            annotation = findClassAnnotation(superclass, annType);
            if (annotation != null)
                return annotation;
        }

        // look at the interface
        // we are programmatic here to only go one step further
        // this is most likely enough for now.
        Class<?>[] interfaces = objType.getInterfaces();
        for (Class<?> ifc : interfaces) {
            annotation = ifc.getAnnotation(annType);
            if (annotation != null)
                break;
        }

        return annotation;
    }
}
