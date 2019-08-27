package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.*
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.util.*

object AMQPTypeIdentifiers {
    fun isPrimitive(type: Type): Boolean = isPrimitive(TypeIdentifier.forGenericType(type))
    fun isPrimitive(typeIdentifier: TypeIdentifier) = typeIdentifier in primitiveTypeNamesByName

    fun primitiveTypeName(type: Type): String =
            primitiveTypeNamesByName[TypeIdentifier.forGenericType(type)] ?:
                    throw NotSerializableException("Primitive type name requested for non-primitive type $type")

    private val primitiveTypeNamesByName = sequenceOf(
            Character::class to "char",
            Char::class to "char",
            Boolean::class to "boolean",
            Byte::class to "byte",
            UnsignedByte::class to "ubyte",
            Short::class to "short",
            UnsignedShort::class to "ushort",
            Int::class to "int",
            UnsignedInteger::class to "uint",
            Long::class to "long",
            UnsignedLong::class to "ulong",
            Float::class to "float",
            Double::class to "double",
            Decimal32::class to "decimal32",
            Decimal64::class to "decimal64",
            Decimal128::class to "decimal128",
            Date::class to "timestamp",
            UUID::class to "uuid",
            ByteArray::class to "binary",
            String::class to "string",
            Symbol::class to "symbol")
            .flatMap { (klass, name) ->
                val typeIdentifier = TypeIdentifier.forClass(klass.javaObjectType)
                val primitiveTypeIdentifier = klass.javaPrimitiveType?.let { TypeIdentifier.forClass(it) }
                if (primitiveTypeIdentifier == null) sequenceOf(typeIdentifier to name)
                else sequenceOf(typeIdentifier to name, primitiveTypeIdentifier to name)
            }.toMap()

    fun nameForType(typeIdentifier: TypeIdentifier): String = when(typeIdentifier) {
        is TypeIdentifier.Erased -> typeIdentifier.name
        is TypeIdentifier.Unparameterised -> primitiveTypeNamesByName[typeIdentifier] ?: typeIdentifier.name
        is TypeIdentifier.UnknownType -> "?"
        is TypeIdentifier.TopType -> Any::class.java.name
        is TypeIdentifier.ArrayOf ->
            if (typeIdentifier == primitiveByteArrayType) "binary"
            else nameForType(typeIdentifier.componentType) +
                    if (typeIdentifier.componentType is TypeIdentifier.Unparameterised &&
                            typeIdentifier.componentType.isPrimitive) "[p]"
                    else "[]"
        is TypeIdentifier.Parameterised -> typeIdentifier.name + typeIdentifier.parameters.joinToString(", ", "<", ">") {
            nameForType(it)
        }
    }

    private val primitiveByteArrayType = TypeIdentifier.ArrayOf(TypeIdentifier.forClass(Byte::class.javaPrimitiveType!!))

    fun nameForType(type: Type): String = nameForType(TypeIdentifier.forGenericType(type))
}
