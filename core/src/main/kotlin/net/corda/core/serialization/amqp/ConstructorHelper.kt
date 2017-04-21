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
 * Annotation indicating a constructor to be used to reconstruct instances of a class during deserialization.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaConstructor

/**
 * Annotation renaming a constructor parameter name, so that it matches a property used during serialization.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaParam(val value: String)

/**
 * Code for finding the constructor we will use for deserialization.
 *
 * If there's only one constructor, it selects that.  If there are two and one is the default, it selects the other.
 * Otherwise it starts with the primary constructor in kotlin, if there is one, and then will override this with any that is
 * annotated with [@CordaConstructor].  It will report an error if more than one constructor is annotated.
 */
fun <T : Any> constructorForDeserialization(clazz: Class<T>): KFunction<T> {
    var preferredCandidate: KFunction<T>? = clazz.kotlin.primaryConstructor
    var annotatedCount = 0
    val ctrctrs = clazz.kotlin.constructors
    var hasDefault = false
    for (ctrctr in ctrctrs) {
        if (ctrctr.parameters.isEmpty()) {
            hasDefault = true
        }
    }
    for (ctrctr in ctrctrs) {
        if (preferredCandidate == null && ctrctrs.size == 1) {
            preferredCandidate = ctrctr
        } else if (preferredCandidate == null && ctrctrs.size == 2 && hasDefault && ctrctr.parameters.isNotEmpty()) {
            preferredCandidate = ctrctr
        } else if (ctrctr.findAnnotation<CordaConstructor>() != null) {
            if (annotatedCount++ > 0) {
                throw NotSerializableException("More than one constructor for $clazz is annotated with @CordaConstructor.")
            }
            preferredCandidate = ctrctr
        }
    }
    return preferredCandidate ?: throw NotSerializableException("No constructor for deserialization found for $clazz.")
}

/**
 * Identifies the properties to be used during serialization by attempting to find those that match the parameters to the
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
                throw NotSerializableException("Property type ${this.genericReturnType} for $name differs from constructor parameter type ${param.type.javaType}")
            }
        } ?: throw NotSerializableException("Property has no getter method for $name")

    }
    return rc
}