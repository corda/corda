package net.corda.nodeapiinterfaces.serialization

/**
 * Annotation indicating a constructor to be used to reconstruct instances of a class during deserialization.
 *
 * NOTE: This is a duplicate of the annotation provided by th net.corda.nodeapi.internal.serialization.amqp
 * package. We can't use that here as node-api depends on core, and we can't just move it into core because
 * that would break the API.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConstructorForDeserialization
