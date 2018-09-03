package net.corda.core

import java.lang.annotation.Inherited

/**
 * This annotation is for interfaces and abstract classes that provide Corda functionality
 * to user applications. Future versions of Corda may add new methods to such interfaces and
 * classes, but will not remove or modify existing methods.
 *
 * Adding new methods does not break Corda's API compatibility guarantee because applications
 * should not implement or extend anything annotated with [DoNotImplement]. These classes are
 * only meant to be implemented by Corda itself.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
annotation class DoNotImplement