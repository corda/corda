package net.corda.core.serialization

/**
 * This annotation marks a class as being a serializer provided by a CorDapp to proxy some third party
 * class Corda couldn't otherwise serialize. It should be applied to classes that implement the
 * [SerializationCustomSerializer] interface
 */
@Target(AnnotationTarget.CLASS)
annotation class CordaCustomSerializer

/**
 *  This annotation marks a class as being a proxy for some third party class and used by some
 *  implementation of [SerializationCustomSerializer]. Such classes must be annotated to allow
 *  them to be discovered in a CorDapp jar and loaded
 */
@Target(AnnotationTarget.CLASS)
annotation class CordaCustomSerializerProxy

