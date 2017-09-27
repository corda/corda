package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import com.google.common.primitives.Primitives
import com.google.common.reflect.TypeToken
import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.beans.IndexedPropertyDescriptor
import java.beans.Introspector
import java.io.NotSerializableException
import java.lang.reflect.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
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
internal fun constructorForDeserialization(type: Type): KFunction<Any>? {
    val clazz: Class<*> = type.asClass()!!
    if (isConcrete(clazz)) {
        var preferredCandidate: KFunction<Any>? = clazz.kotlin.primaryConstructor
        var annotatedCount = 0
        val kotlinConstructors = clazz.kotlin.constructors
        val hasDefault = kotlinConstructors.any { it.parameters.isEmpty() }
        for (kotlinConstructor in kotlinConstructors) {
            if (preferredCandidate == null && kotlinConstructors.size == 1) {
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

        return preferredCandidate?.apply { isAccessible = true }
                ?: throw NotSerializableException("No constructor for deserialization found for $clazz.")
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
internal fun <T : Any> propertiesForSerialization(kotlinConstructor: KFunction<T>?, type: Type, factory: SerializerFactory): Collection<PropertySerializer> {
    val clazz = type.asClass()!!
    return if (kotlinConstructor != null) propertiesForSerializationFromConstructor(kotlinConstructor, type, factory) else propertiesForSerializationFromAbstract(clazz, type, factory)
}

fun isConcrete(clazz: Class<*>): Boolean = !(clazz.isInterface || Modifier.isAbstract(clazz.modifiers))

private fun <T : Any> propertiesForSerializationFromConstructor(kotlinConstructor: KFunction<T>, type: Type, factory: SerializerFactory): Collection<PropertySerializer> {
    val clazz = (kotlinConstructor.returnType.classifier as KClass<*>).javaObjectType
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.groupBy { it.name }.mapValues { it.value[0] }
    val rc: MutableList<PropertySerializer> = ArrayList(kotlinConstructor.parameters.size)
    for (param in kotlinConstructor.parameters) {
        val name = param.name ?: throw NotSerializableException("Constructor parameter of $clazz has no name.")
        val matchingProperty = properties[name] ?:
                throw NotSerializableException("No property matching constructor parameter named $name of $clazz." +
                        " If using Java, check that you have the -parameters option specified in the Java compiler.")
        // Check that the method has a getter in java.
        val getter = matchingProperty.readMethod ?: throw NotSerializableException("Property has no getter method for $name of $clazz." +
                " If using Java and the parameter name looks anonymous, check that you have the -parameters option specified in the Java compiler.")
        val returnType = resolveTypeVariables(getter.genericReturnType, type)
        if (constructorParamTakesReturnTypeOfGetter(returnType, getter.genericReturnType, param)) {
            rc += PropertySerializer.make(name, getter, returnType, factory)
        } else {
            throw NotSerializableException("Property type $returnType for $name of $clazz differs from constructor parameter type ${param.type.javaType}")
        }
    }
    return rc
}

private fun constructorParamTakesReturnTypeOfGetter(getterReturnType: Type, rawGetterReturnType: Type, param: KParameter): Boolean {
    val typeToken = TypeToken.of(param.type.javaType)
    return typeToken.isSupertypeOf(getterReturnType) || typeToken.isSupertypeOf(rawGetterReturnType)
}

private fun propertiesForSerializationFromAbstract(clazz: Class<*>, type: Type, factory: SerializerFactory): Collection<PropertySerializer> {
    // Kotlin reflection doesn't work with Java getters the way you might expect, so we drop back to good ol' beans.
    val properties = Introspector.getBeanInfo(clazz).propertyDescriptors.filter { it.name != "class" }.sortedBy { it.name }.filterNot { it is IndexedPropertyDescriptor }
    val rc: MutableList<PropertySerializer> = ArrayList(properties.size)
    for (property in properties) {
        // Check that the method has a getter in java.
        val getter = property.readMethod ?: throw NotSerializableException("Property has no getter method for ${property.name} of $clazz.")
        val returnType = resolveTypeVariables(getter.genericReturnType, type)
        rc += PropertySerializer.make(property.name, getter, returnType, factory)
    }
    return rc
}

internal fun interfacesForSerialization(type: Type, serializerFactory: SerializerFactory): List<Type> {
    val interfaces = LinkedHashSet<Type>()
    exploreType(type, interfaces, serializerFactory)
    return interfaces.toList()
}

private fun exploreType(type: Type?, interfaces: MutableSet<Type>, serializerFactory: SerializerFactory) {
    val clazz = type?.asClass()
    if (clazz != null) {
        if (clazz.isInterface) {
            if (serializerFactory.whitelist.isNotWhitelisted(clazz)) return // We stop exploring once we reach a branch that has no `CordaSerializable` annotation or whitelisting.
            else interfaces += type
        }
        for (newInterface in clazz.genericInterfaces) {
            if (newInterface !in interfaces) {
                exploreType(resolveTypeVariables(newInterface, type), interfaces, serializerFactory)
            }
        }
        val superClass = clazz.genericSuperclass ?: return
        exploreType(resolveTypeVariables(superClass, type), interfaces, serializerFactory)
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

/**
 * Extension helper for outputting reference to already observed object
 */
fun Data.writeReferencedObject(refObject: ReferencedObject) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(refObject.descriptor)
    putUnsignedInteger(refObject.described)
    exit() // exit described
}

private fun resolveTypeVariables(actualType: Type, contextType: Type?): Type {
    val resolvedType = if (contextType != null) TypeToken.of(contextType).resolveType(actualType).type else actualType
    // TODO: surely we check it is concrete at this point with no TypeVariables
    return if (resolvedType is TypeVariable<*>) {
        val bounds = resolvedType.bounds
        return if (bounds.isEmpty()) SerializerFactory.AnyType else if (bounds.size == 1) resolveTypeVariables(bounds[0], contextType) else throw NotSerializableException("Got bounded type $actualType but only support single bound.")
    } else {
        resolvedType
    }
}

internal fun Type.asClass(): Class<*>? {
    return when {
        this is Class<*> -> this
        this is ParameterizedType -> this.rawType.asClass()
        this is GenericArrayType -> this.genericComponentType.asClass()?.arrayClass()
        this is TypeVariable<*> -> this.bounds.first().asClass()
        this is WildcardType -> this.upperBounds.first().asClass()
        else -> null
    }
}

internal fun Type.asArray(): Type? {
    return when {
        this is Class<*> -> this.arrayClass()
        this is ParameterizedType -> DeserializedGenericArrayType(this)
        else -> null
    }
}

internal fun Class<*>.arrayClass(): Class<*> = java.lang.reflect.Array.newInstance(this, 0).javaClass

internal fun Type.isArray(): Boolean = (this is Class<*> && this.isArray) || (this is GenericArrayType)

internal fun Type.componentType(): Type {
    check(this.isArray()) { "$this is not an array type." }
    return (this as? Class<*>)?.componentType ?: (this as GenericArrayType).genericComponentType
}

internal fun Class<*>.asParameterizedType(): ParameterizedType {
    return DeserializedParameterizedType(this, this.typeParameters)
}

internal fun Type.asParameterizedType(): ParameterizedType {
    return when (this) {
        is Class<*> -> this.asParameterizedType()
        is ParameterizedType -> this
        else -> throw NotSerializableException("Don't know how to convert to ParameterizedType")
    }
}

internal fun Type.isSubClassOf(type: Type): Boolean {
    return TypeToken.of(this).isSubtypeOf(type)
}

// ByteArrays, primtives and boxed primitives are not stored in the object history
internal fun suitableForObjectReference(type: Type): Boolean {
    val clazz = type.asClass()
    return type != ByteArray::class.java && (clazz != null && !clazz.isPrimitive && !Primitives.unwrap(clazz).isPrimitive)
}

/**
 * Common properties that are to be used in the [SerializationContext.properties] to alter serialization behavior/content
 */
internal enum class CommonPropertyNames {
    IncludeInternalInfo,
}

/**
 * Utility function which helps tracking the path in the object graph when exceptions are thrown.
 * Since there might be a chain of nested calls it is useful to record which part of the graph caused an issue.
 * Path information is added to the message of the exception being thrown.
 */
internal inline fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T {
    try {
        return block()
    } catch (th: Throwable) {
        th.setMessage("${strToAppendFn()} -> ${th.message}")
        throw th
    }
}

/**
 * Not a public property so will have to use reflection
 */
private fun Throwable.setMessage(newMsg: String) {
    val detailMessageField = Throwable::class.java.getDeclaredField("detailMessage")
    detailMessageField.isAccessible = true
    detailMessageField.set(this, newMsg)
}

fun ClassWhitelist.requireWhitelisted(type: Type) {
    if (!this.isWhitelisted(type.asClass()!!)) {
        throw NotSerializableException("Class $type is not on the whitelist or annotated with @CordaSerializable.")
    }
}

fun ClassWhitelist.isWhitelisted(clazz: Class<*>) = (hasListed(clazz) || hasAnnotationInHierarchy(clazz))
fun ClassWhitelist.isNotWhitelisted(clazz: Class<*>) = !(this.isWhitelisted(clazz))

// Recursively check the class, interfaces and superclasses for our annotation.
fun ClassWhitelist.hasAnnotationInHierarchy(type: Class<*>): Boolean {
    return type.isAnnotationPresent(CordaSerializable::class.java)
            || type.interfaces.any { hasAnnotationInHierarchy(it) }
            || (type.superclass != null && hasAnnotationInHierarchy(type.superclass))
}
