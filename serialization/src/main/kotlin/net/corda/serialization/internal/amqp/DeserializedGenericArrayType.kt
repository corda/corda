package net.corda.serialization.internal.amqp

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.util.*

/**
 * Implementation of [GenericArrayType] that we can actually construct.
 */
class DeserializedGenericArrayType(private val componentType: Type) : GenericArrayType {
    override fun getGenericComponentType(): Type = componentType
    override fun getTypeName(): String = "${componentType.typeName}[]"
    override fun toString(): String = typeName
    override fun hashCode(): Int = Objects.hashCode(componentType)
    override fun equals(other: Any?): Boolean {
        return other is GenericArrayType && (componentType == other.genericComponentType)
    }
}
