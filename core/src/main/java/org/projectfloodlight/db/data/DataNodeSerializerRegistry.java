package org.projectfloodlight.db.data;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.db.data.serializers.BeanDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.BigDecimalDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.BigIntegerDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.BooleanArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.BooleanDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.ByteArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.ByteDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.CharArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.CharacterDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.DoubleArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.DoubleDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.EnumDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.FloatArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.FloatDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.IntArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.IntegerDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.IterableDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.IteratorDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.LongArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.LongDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.MapDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.ObjectArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.SelfDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.ShortArrayDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.ShortDataNodeSerializer;
import org.projectfloodlight.db.data.serializers.StringDataNodeSerializer;
import org.projectfloodlight.db.util.AnnotationUtils;

public class DataNodeSerializerRegistry {

    private DataNodeSerializerRegistry() {
    }

    private static ConcurrentHashMap<Class<?>, DataNodeSerializer<?>> serializers =
            new ConcurrentHashMap<Class<?>, DataNodeSerializer<?>>();

    /**
     * Returns the custom serializer for the annotated element as specified with
     * the optional BigDBSerialize annotation. The annotated element must be
     * either a class/interface or a method.
     *
     * For a class the custom serializer is used for all instances of that class
     * and its subclasses unless a subclass overrides the custom serializer with
     * its own custom serializer.
     *
     * For a method the custom serializer is applied to the return value of that
     * method. The custom serializer for a method also applies to overrides of
     * that method in subclasses (again unless the overriding method specifies a
     * different custom serializer).
     *
     * @param element Class or method to check for a custom serializer
     * @return
     * @throws BigDBException
     */
    @SuppressWarnings("unchecked")
    public static DataNodeSerializer<?> getCustomSerializer(
            AnnotatedElement element) throws BigDBException {

        DataNodeSerializer<?> serializer = null;

        BigDBSerialize serializeAnnotation = null;
        if (element instanceof Method) {
            serializeAnnotation =
                    AnnotationUtils.findMethodAnnotation((Method) element,
                            BigDBSerialize.class);
        } else if (element instanceof Class<?>) {
            serializeAnnotation =
                    AnnotationUtils.findClassAnnotation((Class<?>) element,
                            BigDBSerialize.class);
        } else {
            throw new UnsupportedOperationException();
        }

        if (serializeAnnotation != null) {
            Class<? extends DataNodeSerializer<?>> serializerClass =
                    serializeAnnotation.using();
            assert serializerClass != null;

            try {
                Method getInstanceMethod =
                        serializerClass.getDeclaredMethod("getInstance");
                if (Modifier.isStatic(getInstanceMethod.getModifiers())) {
                    Object instance = getInstanceMethod.invoke(null);
                    if (instance == null)
                        throw new BigDBException(
                                "Error getting data node serializer instance");
                    serializer = (DataNodeSerializer<?>) instance;
                    return serializer;
                }
            } catch (NoSuchMethodException e) {
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }

            String[] args = serializeAnnotation.args();
            Constructor<?> constructor;
            try {
                Class<?> argsClass = args.getClass();
                try {
                    constructor = serializerClass.getConstructor(argsClass);
                    serializer =
                            (DataNodeSerializer<Object>) constructor
                                    .newInstance((Object) args);
                } catch (NoSuchMethodException exc2) {
                    if (args.length > 0) {
                        throw new BigDBException(
                                "Serializer args specified for a serializer " +
                                "that doesn't accept/expect args");

                    }
                    constructor = serializerClass.getConstructor();
                    serializer =
                            (DataNodeSerializer<?>) constructor.newInstance();
                }
            } catch (NoSuchMethodException exc) {
                throw new BigDBException("No appropriate "
                        + "constructor for custom serializer.", exc);
            } catch (Exception exc) {
                throw new BigDBException("Error instantiating "
                        + "custom serializer.", exc);
            }
        }

        return serializer;
    }

    private static DataNodeSerializer<?> newDataNodeSerializer(
            Class<?> implClass) throws BigDBException {
        // Check for a custom serializer
        DataNodeSerializer<?> dataNodeSerializer = null;
        if (DataNodeSerializer.class.isAssignableFrom(implClass)) {
            return SelfDataNodeSerializer.getInstance();
        }

        dataNodeSerializer = getCustomSerializer(implClass);
        if (dataNodeSerializer != null)
            return dataNodeSerializer;

        if (String.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = StringDataNodeSerializer.getInstance();
        } else if ((implClass == boolean.class) || Boolean.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = BooleanDataNodeSerializer.getInstance();
        } else if ((implClass == byte.class) || Byte.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = ByteDataNodeSerializer.getInstance();
        } else if ((implClass == char.class) || Character.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = CharacterDataNodeSerializer.getInstance();
        } else if ((implClass == short.class) || Short.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = ShortDataNodeSerializer.getInstance();
        } else if ((implClass == int.class) || Integer.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = IntegerDataNodeSerializer.getInstance();
        } else if ((implClass == long.class) || Long.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = LongDataNodeSerializer.getInstance();
        } else if ((implClass == float.class) || Float.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = FloatDataNodeSerializer.getInstance();
        } else if ((implClass == double.class) || Double.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = DoubleDataNodeSerializer.getInstance();
        } else if (BigInteger.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = BigIntegerDataNodeSerializer.getInstance();
        } else if (BigDecimal.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = BigDecimalDataNodeSerializer.getInstance();
        } else if (Enum.class.isAssignableFrom(implClass)) {
            // Need to use "Enum.class.isAssignableFrom" instead of isEnum here
            // to handle enums with overrides of default enum behavior, for
            // example overriding the toString function for an enum constant.
            // This creates a subclass of the Enum class and for some reason
            // isEnum only returns true if it's a direct instance of the
            // Enum class, not if it's a subclass. Probably don't need to
            // include the check for "implClass.isEnum" here anymore, but
            // just to be safe...
            dataNodeSerializer = EnumDataNodeSerializer.getInstance();
        } else if (implClass.isArray()) {
            Class<?> componentType = implClass.getComponentType();
            if (componentType == byte.class) {
                dataNodeSerializer = ByteArrayDataNodeSerializer.getInstance();
            } else if (componentType == char.class) {
                dataNodeSerializer = CharArrayDataNodeSerializer.getInstance();
            } else if (componentType == boolean.class) {
                dataNodeSerializer =
                        BooleanArrayDataNodeSerializer.getInstance();
            } else if (componentType == short.class) {
                dataNodeSerializer = ShortArrayDataNodeSerializer.getInstance();
            } else if (componentType == int.class) {
                dataNodeSerializer = IntArrayDataNodeSerializer.getInstance();
            } else if (componentType == long.class) {
                dataNodeSerializer = LongArrayDataNodeSerializer.getInstance();
            } else if (componentType == float.class) {
                dataNodeSerializer = FloatArrayDataNodeSerializer.getInstance();
            } else if (componentType == double.class) {
                dataNodeSerializer =
                        DoubleArrayDataNodeSerializer.getInstance();
            } else {
                dataNodeSerializer =
                        ObjectArrayDataNodeSerializer.getInstance();
            }
        } else if (Iterable.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = IterableDataNodeSerializer.getInstance();
        } else if (Iterator.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = IteratorDataNodeSerializer.getInstance();
        } else if (Map.class.isAssignableFrom(implClass)) {
            dataNodeSerializer = MapDataNodeSerializer.getInstance();
        } else {
            dataNodeSerializer = new BeanDataNodeSerializer(implClass);
        }
        return dataNodeSerializer;
    }

    @SuppressWarnings("unchecked")
    public static DataNodeSerializer<Object> getDataNodeSerializer(
            Class<?> implClass) throws BigDBException {
        DataNodeSerializer<?> dataNodeSerializer = serializers.get(implClass);
        if (dataNodeSerializer == null) {
            synchronized(DataNodeSerializerRegistry.class) {
                dataNodeSerializer = serializers.get(implClass);
                if (dataNodeSerializer == null) {
                    dataNodeSerializer = newDataNodeSerializer(implClass);
                    if (dataNodeSerializer != null)
                        registerDataNodeSerializer(implClass, dataNodeSerializer);
                }
            }
        }
        return (DataNodeSerializer<Object>) dataNodeSerializer;
    }

    public static void registerDataNodeSerializer(Class<?> implClass,
            DataNodeSerializer<?> dataNodeSerializer) throws BigDBException {
        synchronized(DataNodeSerializerRegistry.class) {
            DataNodeSerializer<?> existingDataNodeSerializer =
                    serializers.putIfAbsent(implClass, dataNodeSerializer);
            if (existingDataNodeSerializer != null) {
                throw new BigDBException(String.format(
                        "Data node serializer for class \"%s\" already registered",
                        implClass));
            }
        }
    }

    public static void serializeObject(Object object,
            DataNodeGenerator generator) throws BigDBException {
        if (object == null) {
            generator.writeNull();
        } else {
            Class<?> elementClass = object.getClass();
            DataNodeSerializer<Object> dataNodeSerializer =
                    DataNodeSerializerRegistry
                            .getDataNodeSerializer(elementClass);
            dataNodeSerializer.serialize(object, generator);
        }
    }
}
