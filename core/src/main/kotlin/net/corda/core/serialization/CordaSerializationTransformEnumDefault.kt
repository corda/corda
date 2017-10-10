package net.corda.core.serialization

/**
 *
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializationTransformEnumDefaults(vararg val value: CordaSerializationTransformEnumDefault)

/**
 *
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// When Kotlin starts writing 1.8 class files enable this, it removes the need for the wrapping annotation
//@Repeatable
annotation class CordaSerializationTransformEnumDefault(val new: String, val old: String)

