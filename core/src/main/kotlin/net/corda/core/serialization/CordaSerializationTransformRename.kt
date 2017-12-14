package net.corda.core.serialization

/**
 * This annotation is used to mark a class as having had multiple elements renamed as a container annotation for
 * instances of [CordaSerializationTransformRename], each of which details an individual rename.
 *
 * @property value an array of [CordaSerializationTransformRename]
 *
 * NOTE: Order is important, new values should always be added before existing
 *
 * IMPORTANT - Once added (and in production) do NOT remove old annotations. See documentation for
 * more discussion on this point!.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializationTransformRenames(vararg val value: CordaSerializationTransformRename)

// TODO When we have class renaming update the docs
/**
 * This annotation is used to mark a class has having had a property element. It is used by the
 * AMQP deserializer to allow instances with different versions of the class on their Class Path
 * to successfully deserialize the object.
 *
 * NOTE: Renaming of the class itself isn't done with this annotation or, at present, supported
 * by Corda
 *
 * @property to [String] representation of the properties new name
 * @property from [String] representation of the properties old new
 *
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// When Kotlin starts writing 1.8 class files enable this, it removes the need for the wrapping annotation
//@Repeatable
annotation class CordaSerializationTransformRename(val to: String, val from: String)
