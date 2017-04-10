package net.corda.core.serialization.amqp

import java.io.NotSerializableException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaMethod

@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaConstructor

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaParam(val name: String)

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

fun <T : Any> propertiesForSerialization(ctrctr: KFunction<T>): Collection<PropertySerializer> {
    val properties: Map<String, KProperty1<T, *>> = (ctrctr.returnType.classifier as KClass<T>).memberProperties.groupBy { it.name }.mapValues { it.value.get(0) }
    val rc: MutableList<PropertySerializer> = mutableListOf()
    for (param in ctrctr.parameters) {
        val name = param.findAnnotation<CordaParam>()?.name ?: param.name ?: throw NotSerializableException("Property has no name for constructor parameter.")
        val matchingProperty = properties[name] ?: throw NotSerializableException("No property matching constructor parameter named $name")
        // TODO: Check property type, not just name!
        // Check that the method has a getter in java.
        matchingProperty.getter.javaMethod?.apply {
            rc += PropertySerializer.make(name, this)
        } ?: throw NotSerializableException("Property has no getter method for $name")

    }
    return rc
}