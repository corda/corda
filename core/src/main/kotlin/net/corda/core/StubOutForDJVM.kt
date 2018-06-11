package net.corda.core

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * We expect that almost every non-deterministic element can have its bytecode
 * deleted entirely from the deterministic version of Corda. This annotation is
 * for those (hopefully!) few occasions where the non-deterministic function
 * cannot be deleted. In these cases, the function will be stubbed out instead.
 */
// DOCSTART 01
@Target(
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER
)
@Retention(BINARY)
@CordaInternal
annotation class StubOutForDJVM
// DOCEND 01
