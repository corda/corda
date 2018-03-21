package net.corda.example.method;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target({TYPE, METHOD})
@Retention(CLASS)
@CordaInternal
public @interface LocalInvisibleAnnotation {
}
