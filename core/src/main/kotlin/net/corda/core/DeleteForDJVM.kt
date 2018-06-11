package net.corda.core

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

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
