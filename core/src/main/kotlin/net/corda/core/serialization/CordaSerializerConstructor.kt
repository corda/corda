package net.corda.core.serialization

/**
 * This annotation is a marker to indicate which secondary constructors shuold be considered, and in which
 * order, for evolving objects during their deserialisation.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializerConstructor(val version: Int)


