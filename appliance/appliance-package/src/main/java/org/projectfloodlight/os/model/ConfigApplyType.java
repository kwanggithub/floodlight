package org.projectfloodlight.os.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.projectfloodlight.os.IOSConfiglet.ConfigType;

/**
 * Annotation on config model that indicates the which configlets would be
 * interested in the data
 * @author readams
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ConfigApplyType {
    /**
     * Changing this value will cause configlets that provide the
     * associated {@link ConfigType} to be executed
     * @return the {@link ConfigType} for the method
     */
    ConfigType[] value();
}
