package net.bigdb.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.bigdb.data.DataNodeSerializer;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface BigDBSerialize {
    public Class<? extends DataNodeSerializer<?>> using();
    public String[] args() default {};
}
