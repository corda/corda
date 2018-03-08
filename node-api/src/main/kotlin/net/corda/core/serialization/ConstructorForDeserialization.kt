package net.corda.core.serialization

/**
 * Annotation indicating a constructor to be used to reconstruct instances of a class during deserialization.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConstructorForDeserialization