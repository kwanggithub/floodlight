package org.sdnplatform.os.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.sdnplatform.os.IOSActionlet.ActionType;

/**
 * Annotation on action model that indicates the which actionlets would be
 * interested in the data
 * @author readams
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ActionApplyType {
    /**
     * Changing this value will cause actionlets that provide the
     * associated {@link ActionType} to be executed
     * @return the {@link ActionType} for the method
     */
    ActionType[] value();
}
