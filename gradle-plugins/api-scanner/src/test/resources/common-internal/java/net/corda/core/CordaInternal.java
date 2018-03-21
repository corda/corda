package net.corda.core;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target({TYPE, METHOD, FIELD})
@Retention(CLASS)
public @interface CordaInternal {
}
