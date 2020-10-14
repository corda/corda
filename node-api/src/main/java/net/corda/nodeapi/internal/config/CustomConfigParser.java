package net.corda.nodeapi.internal.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used to provide ConfigParser for the class,
 * the [parseAs] method will use the provided parser instead of data class constructs to parse the object.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomConfigParser {
    Class parser();
}
