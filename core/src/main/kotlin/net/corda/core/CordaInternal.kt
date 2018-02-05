package net.corda.core

import java.lang.annotation.Inherited

/**
 * These fields are only meant to be used by Corda internally, and are not intended to be part of the public API.
 *
 * These methods are not part of Corda's API compatibility guarantee and applications should not use them.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
@Inherited
annotation class CordaInternal

