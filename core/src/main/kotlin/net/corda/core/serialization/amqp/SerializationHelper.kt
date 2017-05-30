package net.corda.core.serialization.amqp

import com.google.common.reflect.TypeToken
import org.apache.qpid.proton.codec.Data
import java.beans.Introspector
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
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
 * Code for finding the constructor we will use for deserialization.
 *
 * If there's only one constructor, it selects that.  If there are two and one is the default, it selects the other.
 * Otherwise it starts with the primary constructor in kotlin, if there is one, and then will override this with any that is
 * annotated with [@CordaConstructor].  It will report an error if more than one constructor is annotated.
 */
internal fun <T : Any> constructorForDeserialization(clazz: Class<T>): KFunction<T>? {
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
 * Note, you will need any Java classes to be compiled with the `-parameters` option to ensure constructor parameters have
 * names accessible via reflection.
 */
internal fun <T : Any> propertiesForSerialization(kotlinConstructor: KFunction<T>?, clazz: Class<*>, factory: SerializerFactory): Collection<PropertySerializer> {
    return if (kotlinConstructor != null) propertiesForSerialization(kotlinConstructor, factory) else propertiesForSerialization(clazz, factory)
}

private fun isConcrete(clazz: Class<*>): Boolean = !(clazz.isInterface || Modifier.isAbstract(clazz.modifiers))

private fun <T : Any> propertiesForSerialization(kotlinConstructor: KFunction<T>, factory: SerializerFactory): Collection<PropertySerializer> {
    val clazz = (kotlinConstructor.returnType.classifier as KClass<*>).javaObjectType
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.groupBy { it.name }.mapValues { it.value[0] }
    val rc: MutableList<PropertySerializer> = ArrayList(kotlinConstructor.parameters.size)
    for (param in kotlinConstructor.parameters) {
        val name = param.name ?: throw NotSerializableException("Constructor parameter of $clazz has no name.")
        val matchingProperty = properties[name] ?: throw NotSerializableException("No property matching constructor parameter named $name of $clazz." +
                " If using Java, check that you have the -parameters option specified in the Java compiler.")
        // Check that the method has a getter in java.
        val getter = matchingProperty.readMethod ?: throw NotSerializableException("Property has no getter method for $name of $clazz." +
                " If using Java and the parameter name looks anonymous, check that you have the -parameters option specified in the Java compiler.")
        if (constructorParamTakesReturnTypeOfGetter(getter, param)) {
            rc += PropertySerializer.make(name, getter, factory)
        } else {
            throw NotSerializableException("Property type ${getter.genericReturnType} for $name of $clazz differs from constructor parameter type ${param.type.javaType}")
        }
    }
    return rc
}

private fun constructorParamTakesReturnTypeOfGetter(getter: Method, param: KParameter): Boolean = TypeToken.of(param.type.javaType).isSupertypeOf(getter.genericReturnType)

private fun propertiesForSerialization(clazz: Class<*>, factory: SerializerFactory): Collection<PropertySerializer> {
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.sortedBy { it.name }
    val rc: MutableList<PropertySerializer> = ArrayList(properties.size)
    for (property in properties) {
        // Check that the method has a getter in java.
        val getter = property.readMethod ?: throw NotSerializableException("Property has no getter method for ${property.name} of $clazz.")
        rc += PropertySerializer.make(property.name, getter, factory)
    }
    return rc
}

internal fun interfacesForSerialization(clazz: Class<*>): List<Type> {
    val interfaces = LinkedHashSet<Type>()
    exploreType(clazz, interfaces)
    return interfaces.toList()
}

private fun exploreType(type: Type?, interfaces: MutableSet<Type>) {
    val clazz = (type as? Class<*>) ?: (type as? ParameterizedType)?.rawType as? Class<*>
    if (clazz != null) {
        if (clazz.isInterface) interfaces += clazz
        for (newInterface in clazz.genericInterfaces) {
            if (newInterface !in interfaces) {
                interfaces += newInterface
                exploreType(newInterface, interfaces)
            }
        }
        exploreType(clazz.genericSuperclass, interfaces)
    }
}

/**
 * Extension helper for writing described objects.
 */
fun Data.withDescribed(descriptor: Descriptor, block: Data.() -> Unit) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(descriptor.code ?: descriptor.name)
    block()
    exit() // exit described
}

/**
 * Extension helper for writing lists.
 */
fun Data.withList(block: Data.() -> Unit) {
    // Write list
    putList()
    enter()
    block()
    exit() // exit list
}