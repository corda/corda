package net.corda.core

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * Declare the annotated element to be non-deterministic, which means that its bytecode
 * will be deleted from the .class file when creating the deterministic version of Corda
 * Core.
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
annotation class NonDeterministic
// DOCEND 01
