package org.projectfloodlight.db.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.restlet.engine.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Method("PATCH")
public @interface Patch {
    String value() default "";
}
