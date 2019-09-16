package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.core.serialization.*
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.*

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

fun resolveTypeVariables(actualType: Type, contextType: Type?): Type {
    val resolvedType = if (contextType != null) TypeToken.of(contextType).resolveType(actualType).type else actualType
    // TODO: surely we check it is concrete at this point with no TypeVariables
    return if (resolvedType is TypeVariable<*>) {
        val bounds = resolvedType.bounds
        return if (bounds.isEmpty()) {
            TypeIdentifier.UnknownType.getLocalType()
        } else if (bounds.size == 1) {
            resolveTypeVariables(bounds[0], contextType)
        } else throw AMQPNotSerializableException(
                actualType,
                "Got bounded type $actualType but only support single bound.")
    } else {
        resolvedType
    }
}

internal fun Type.asClass(): Class<*> {
    return when(this) {
        is Class<*> -> this
        is ParameterizedType -> this.rawType.asClass()
        is GenericArrayType -> this.genericComponentType.asClass().arrayClass()
        is TypeVariable<*> -> this.bounds.first().asClass()
        is WildcardType -> this.upperBounds.first().asClass()
        // Per https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Type.html,
        // there is nothing else that it can be, so this can never happen.
        else -> throw UnsupportedOperationException("Cannot convert $this to class")
    }
}

internal fun Type.asArray(): Type? {
    return when(this) {
        is Class<*>,
        is ParameterizedType -> TypeIdentifier.ArrayOf(TypeIdentifier.forGenericType(this))
                .getLocalType(this::class.java.classLoader ?: TypeIdentifier::class.java.classLoader)
        else -> null
    }
}

internal fun Class<*>.arrayClass(): Class<*> = java.lang.reflect.Array.newInstance(this, 0).javaClass

internal fun Type.isArray(): Boolean = (this is Class<*> && this.isArray) || (this is GenericArrayType)

internal fun Type.componentType(): Type {
    check(this.isArray()) { "$this is not an array type." }
    return (this as? Class<*>)?.componentType ?: (this as GenericArrayType).genericComponentType
}

internal fun Class<*>.asParameterizedType(): ParameterizedType =
    TypeIdentifier.Erased(this.name, this.typeParameters.size)
            .toParameterized(this.typeParameters.map { TypeIdentifier.forGenericType(it) })
            .getLocalType(classLoader ?: TypeIdentifier::class.java.classLoader) as ParameterizedType

internal fun Type.asParameterizedType(): ParameterizedType {
    return when (this) {
        is Class<*> -> this.asParameterizedType()
        is ParameterizedType -> this
        else -> throw AMQPNotSerializableException(this, "Don't know how to convert to ParameterizedType")
    }
}

internal fun Type.isSubClassOf(type: Type): Boolean {
    return TypeToken.of(this).isSubtypeOf(TypeToken.of(type).rawType)
}

/**
 * Common properties that are to be used in the [SerializationContext.properties] to alter serialization behavior/content
 */
enum class CommonPropertyNames {
    IncludeInternalInfo,
}

fun ClassWhitelist.requireWhitelisted(type: Type) {
    // See CORDA-2782 for explanation of the special exemption made for Comparable
    if (!this.isWhitelisted(type.asClass()) && type.asClass() != java.lang.Comparable::class.java) {
        throw AMQPNotSerializableException(
                type,
                "Class \"$type\" is not on the whitelist or annotated with @CordaSerializable.")
    }
}

fun ClassWhitelist.isWhitelisted(clazz: Class<*>) = hasListed(clazz) || hasCordaSerializable(clazz)
fun ClassWhitelist.isNotWhitelisted(clazz: Class<*>) = !this.isWhitelisted(clazz)

/**
 * Check the given [Class] has the [CordaSerializable] annotation, either directly or inherited from any of its super
 * classes or interfaces.
 */
fun hasCordaSerializable(type: Class<*>): Boolean {
    return type.isAnnotationPresent(CordaSerializable::class.java)
            || type.interfaces.any(::hasCordaSerializable)
            || (type.superclass != null && hasCordaSerializable(type.superclass))
}