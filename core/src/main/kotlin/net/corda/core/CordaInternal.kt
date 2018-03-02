package net.corda.core

/**
 * These methods are not part of Corda's API compatibility guarantee and applications should not use them.
 *
 * These fields are only meant to be used by Corda internally, and are not intended to be part of the public API.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class CordaInternal