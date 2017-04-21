package net.corda.core.serialization.amqp

import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.io.NotSerializableException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Annotation indicator a constructor to be used to reconstruct instances of a class during deserialization.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaConstructor

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaParam(val value: String)

/**
 * Code for finding the constructor we will use for deserialization.
 *
 * It starts with the primary constructor in kotlin if there is one, and then will override this with any that is
 * annotated with [@CordaConstructor].  It will report an error if more than one constructor is annotated.
 */
fun <T : Any> constructorForDeserialization(clazz: Class<T>): KFunction<T> {
    var preferredCandidate: KFunction<T>? = clazz.kotlin.primaryConstructor
    var annotatedCount = 0
    for (ctrctr in clazz.kotlin.constructors) {
        if (ctrctr.findAnnotation<CordaConstructor>() != null) {
            if (annotatedCount++ > 0) {
                throw NotSerializableException("More than one constructor for $clazz is annotated with @CordaConstructor.")
            }
            preferredCandidate = ctrctr
        }
    }
    return preferredCandidate ?: throw NotSerializableException("No constructor for deserialization found for $clazz.")
}

/**
 * Identifies the properties to be used during serialization by attempting to find those that match the arguments to the
 * deserialization constructor.
 *
 * It's possible to effectively rename a constructor parameter so it matches a property using the [@CordaParam] annotation.
 */
fun <T : Any> propertiesForSerialization(ctrctr: KFunction<T>): Collection<PropertySerializer> {
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties: Map<String, PropertyDescriptor> = Introspector.getBeanInfo((ctrctr.returnType.classifier as KClass<T>).javaObjectType).propertyDescriptors.filter { it.name != "class" }.groupBy { it.name }.mapValues { it.value[0] }
    val rc: MutableList<PropertySerializer> = mutableListOf()
    for (param in ctrctr.parameters) {
        val name = param.findAnnotation<CordaParam>()?.value ?: param.name ?: throw NotSerializableException("Property has no name for constructor parameter.")
        val matchingProperty = properties[name] ?: throw NotSerializableException("No property matching constructor parameter named $name")
        // Check that the method has a getter in java.
        matchingProperty.readMethod?.apply {
            if (this.genericReturnType == param.type.javaType) {
                rc += PropertySerializer.make(name, this)
            } else {
                throw NotSerializableException("Property type ${this.genericReturnType} for $name differs from constructor type ${param.type.javaType}")
            }
        } ?: throw NotSerializableException("Property has no getter method for $name")

    }
    return rc
}