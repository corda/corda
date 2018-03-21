package net.corda.example.field;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target({TYPE, FIELD})
@Retention(CLASS)
@CordaInternal
public @interface LocalInvisibleAnnotation {
}
