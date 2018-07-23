package net.corda.core

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

/**
 * Declare the annotated element to unsuitable for the deterministic version of Corda.
 */
// DOCSTART 01
@Target(
    FILE,
    CLASS,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    PROPERTY,
    FIELD,
    TYPEALIAS
)
@Retention(BINARY)
@CordaInternal
annotation class DeleteForDJVM
// DOCEND 01
