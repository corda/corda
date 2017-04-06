package net.corda.core.serialization.amqp

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type

class DeserializedGenericArrayType(private val componentType: Type) : GenericArrayType {
    override fun getGenericComponentType(): Type = componentType
    override fun getTypeName(): String = "${componentType.typeName}[]"
    override fun toString(): String = typeName
    override fun hashCode(): Int = componentType.hashCode() * 31
    override fun equals(other: Any?): Boolean {
        return other is GenericArrayType && componentType.equals(other.genericComponentType)
    }
}