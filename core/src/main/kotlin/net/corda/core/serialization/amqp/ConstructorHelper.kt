package net.corda.core.serialization.amqp

import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.io.NotSerializableException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Annotation indicating a constructor to be used to reconstruct instances of a class during deserialization.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConstructorForDeserialization

/**
 * Annotation renaming a constructor parameter name, so that it matches a property used during serialization.
 *
 * This annotation is primarily aimed at Java developers to work around if they haven't added -parameters to the compiler
 * and thus the parameters can't be matched to the properties/getters. It will also rename a constructor parameter for
 * any other reason, but it doesn't influence what goes into the schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SerializedName(val value: String)

/**
 * Code for finding the constructor we will use for deserialization.
 *
 * If there's only one constructor, it selects that.  If there are two and one is the default, it selects the other.
 * Otherwise it starts with the primary constructor in kotlin, if there is one, and then will override this with any that is
 * annotated with [@CordaConstructor].  It will report an error if more than one constructor is annotated.
 */
fun <T : Any> constructorForDeserialization(clazz: Class<T>): KFunction<T>? {
    if (isConcrete(clazz)) {
        var preferredCandidate: KFunction<T>? = clazz.kotlin.primaryConstructor
        var annotatedCount = 0
        val kotlinConstructors = clazz.kotlin.constructors
        val hasDefault = kotlinConstructors.any { it.parameters.isEmpty() }
        for (kotlinConstructor in kotlinConstructors) {
            if (preferredCandidate == null && kotlinConstructors.size == 1 && !hasDefault) {
                preferredCandidate = kotlinConstructor
            } else if (preferredCandidate == null && kotlinConstructors.size == 2 && hasDefault && kotlinConstructor.parameters.isNotEmpty()) {
                preferredCandidate = kotlinConstructor
            } else if (kotlinConstructor.findAnnotation<ConstructorForDeserialization>() != null) {
                if (annotatedCount++ > 0) {
                    throw NotSerializableException("More than one constructor for $clazz is annotated with @CordaConstructor.")
                }
                preferredCandidate = kotlinConstructor
            }
        }
        return preferredCandidate ?: throw NotSerializableException("No constructor for deserialization found for $clazz.")
    } else {
        return null
    }
}

/**
 * Identifies the properties to be used during serialization by attempting to find those that match the parameters to the
 * deserialization constructor, if the class is concrete.  If it is abstract, or an interface, then use all the properties.
 *
 * It's possible to effectively rename a constructor parameter so it matches a property using the [@CordaParam] annotation.
 */
fun <T : Any> propertiesForSerialization(kotlinConstructor: KFunction<T>?, clazz: Class<*>): Collection<PropertySerializer> {
    return if (kotlinConstructor != null) propertiesForSerialization(kotlinConstructor) else propertiesForSerialization(clazz)
}

private fun isConcrete(clazz: Class<*>): Boolean = !(clazz.isInterface || Modifier.isAbstract(clazz.modifiers))

private fun <T : Any> propertiesForSerialization(kotlinConstructor: KFunction<T>): Collection<PropertySerializer> {
    val clazz = (kotlinConstructor.returnType.classifier as KClass<*>).javaObjectType
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties: Map<String, PropertyDescriptor> = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.groupBy { it.name }.mapValues { it.value[0] }
    val rc: MutableList<PropertySerializer> = ArrayList(kotlinConstructor.parameters.size)
    for (param in kotlinConstructor.parameters) {
        val name = param.findAnnotation<SerializedName>()?.value ?: param.name ?: throw NotSerializableException("Constructor parameter of $clazz has no name. See the documentation for @SerializedName.")
        val matchingProperty = properties[name] ?: throw NotSerializableException("No property matching constructor parameter named $name of $clazz. See the documentation for @SerializedName.")
        // Check that the method has a getter in java.
        val getter = matchingProperty.readMethod ?: throw NotSerializableException("Property has no getter method for $name of $clazz.")
        if (getter.genericReturnType == param.type.javaType) {
            rc += PropertySerializer.make(name, getter)
        } else {
            throw NotSerializableException("Property type ${getter.genericReturnType} for $name of $clazz differs from constructor parameter type ${param.type.javaType}")
        }
    }
    return rc
}

private fun propertiesForSerialization(clazz: Class<*>): Collection<PropertySerializer> {
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.sortedBy { it.name }
    val rc: MutableList<PropertySerializer> = ArrayList(properties.size)
    for (property in properties) {
        // Check that the method has a getter in java.
        val getter = property.readMethod ?: throw NotSerializableException("Property has no getter method for ${property.name} of $clazz.")
        rc += PropertySerializer.make(property.name, getter)
    }
    return rc
}