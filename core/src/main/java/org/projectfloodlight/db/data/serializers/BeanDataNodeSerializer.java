package org.projectfloodlight.db.data.serializers;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.db.data.DataNodeSerializerRegistry;
import org.projectfloodlight.db.data.annotation.BigDBIgnore;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.util.AnnotationUtils;

public final class BeanDataNodeSerializer implements DataNodeSerializer<Object> {

    private static final class PropertyInfo {

        final Method readMethod;
        final DataNodeSerializer<?> serializer;

        PropertyInfo(Method readMethod, DataNodeSerializer<?> serializer) {
            this.readMethod = readMethod;
            this.serializer = serializer;
        }
    }

    // Map from the name of a property to the serializer for that property
    private final Map<String, PropertyInfo> properties =
            new TreeMap<String, PropertyInfo>();

    public BeanDataNodeSerializer(Class<?> beanClass) throws BigDBException {
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(beanClass);
        } catch (IntrospectionException e) {
            throw new BigDBException("Error inspecting object class" +
                    beanClass.getSimpleName() + ": " + e.getMessage(), e);
        }

        // Iterate over the properties and write them out.
        for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {

            Method readMethod = descriptor.getReadMethod();

            // We only handle methods currently, not fields, so
            // skip this property if it's not a method
            if (readMethod == null)
                continue;

            // Check to see if this getter has been annotated to
            // suppress inclusion in the bean writer output. This is
            // so that getters that would cause loops can be skipped.
            Annotation ignoreAnnotation =
                    AnnotationUtils.findMethodAnnotation(readMethod,
                            BigDBIgnore.class);
            if (ignoreAnnotation != null)
                continue;

            // Don't include properties inherited from the base
            // Object class, e.g. getClass
            if (readMethod.getDeclaringClass().equals(Object.class))
                continue;

            String propertyName = descriptor.getName();
            BigDBProperty propertyAnnotation =
                    AnnotationUtils.findMethodAnnotation(readMethod,
                            BigDBProperty.class);
            if (propertyAnnotation != null)
                propertyName = propertyAnnotation.value();

            DataNodeSerializer<?> dataNodeSerializer =
                    DataNodeSerializerRegistry.getCustomSerializer(readMethod);

            if (dataNodeSerializer == null) {
                Class<?> returnTypeClass = readMethod.getReturnType();
                dataNodeSerializer =
                        DataNodeSerializerRegistry
                                .getDataNodeSerializer(returnTypeClass);
            }

            PropertyInfo propertyInfo =
                    new PropertyInfo(readMethod, dataNodeSerializer);
            properties.put(propertyName, propertyInfo);
        }
    }

    public Set<String> getAllPropertyNames() {
        return properties.keySet();
    }

    private static Object getPropertyValue(Object object, String propertyName,
            Method readMethod) throws BigDBException {
        try {
            Object propertyValue = readMethod.invoke(object, (Object[]) null);
            return propertyValue;
        } catch (IllegalArgumentException e) {
            throw new BigDBException("Illegal argument reading property " +
                    propertyName + " of class " +
                    object.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        } catch (IllegalAccessException e) {
            throw new BigDBException("Illegal access reading property " +
                    propertyName + " of class " +
                    object.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        } catch (InvocationTargetException e) {
            throw new BigDBException(
                    "Invokation Target error reading property " + propertyName +
                            " of class " + object.getClass().getSimpleName() +
                            ": " + e.getTargetException().getMessage(), e
                            .getTargetException());
        }
    }

    public void serializeProperty(Object object, String propertyName,
            DataNodeGenerator generator)
            throws BigDBException {
        PropertyInfo propertyInfo = properties.get(propertyName);
        if (propertyInfo != null) {
            Object propertyValue = getPropertyValue(object, propertyName,
                    propertyInfo.readMethod);
            if (propertyValue == null) {
                generator.writeNull();
            } else {
                @SuppressWarnings("unchecked")
                DataNodeSerializer<Object> serializer =
                        (DataNodeSerializer<Object>) propertyInfo.serializer;
                serializer.serialize(propertyValue, generator);
            }
        }
    }

    @Override
    public void serialize(Object object, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeBean(object);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Properties: {\n");
        for (Map.Entry<String, PropertyInfo> entry: properties.entrySet()) {
            String name = entry.getKey();
            PropertyInfo propertyInfo = entry.getValue();
            builder.append(String.format("%s: {%s,%s},%n", name,
                    propertyInfo.readMethod, propertyInfo.serializer));
        }
        builder.append("}");
        return builder.toString();
    }
}
