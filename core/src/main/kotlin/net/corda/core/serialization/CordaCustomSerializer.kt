package net.corda.core.serialization

/**
 * This annotation marks a class as being a serializer provided by a CorDapp to proxy some third party
 * class Corda couldn't otherwise serialize. It should be applied to classes that implement the
 * [SerializationCustomSerializer] interface
 */
@Target(AnnotationTarget.CLASS)
annotation class CordaCustomSerializer

