package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.utilities.loggerFor
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Base class for serialization of a property of an object.
 */
sealed class PropertySerializer(val name: String, val readMethod: Method?, val resolvedType: Type) {
    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = resolvedType.asClass()?.isInterface == true
    private val isJVMPrimitive: Boolean get() = resolvedType.asClass()?.isPrimitive == true

    private fun generateType(): String {
        return if (isInterface || resolvedType == Any::class.java) "*" else SerializerFactory.nameForType(resolvedType)
    }

    private fun generateRequires(): List<String> {
        return if (isInterface) listOf(SerializerFactory.nameForType(resolvedType)) else emptyList()
    }

    private fun generateDefault(): String? {
        if (isJVMPrimitive) {
            return when (resolvedType) {
                java.lang.Boolean.TYPE -> "false"
                java.lang.Character.TYPE -> "&#0"
                else -> "0"
            }
        } else {
            return null
        }
    }

    private fun generateMandatory(): Boolean {
        return isJVMPrimitive || readMethod?.returnsNullable() == false
    }

    private fun Method.returnsNullable(): Boolean {
        try {
            val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull { it.javaGetter == this }?.returnType?.toString() ?: "?"
            return returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
        } catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
            // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue is: https://youtrack.jetbrains.com/issue/KT-13077
            // TODO: Revisit this when Kotlin issue is fixed.
            logger.error("Unexpected internal Kotlin error", e)
            return true
        }
    }

    companion object {
        private val logger = loggerFor<PropertySerializer>()

        fun make(name: String, readMethod: Method?, resolvedType: Type, factory: SerializerFactory): PropertySerializer {
            readMethod?.isAccessible = true
            if (SerializerFactory.isPrimitive(resolvedType)) {
                return when (resolvedType) {
                    Char::class.java, Character::class.java -> AMQPCharPropertySerializer(name, readMethod)
                    else -> AMQPPrimitivePropertySerializer(name, readMethod, resolvedType)
                }
            } else {
                return DescribedTypePropertySerializer(name, readMethod, resolvedType) { factory.get(null, resolvedType) }
            }
        }
    }

    /**
     * A property serializer for a complex type (another object).
     */
    class DescribedTypePropertySerializer(
            name: String, readMethod: Method?,
            resolvedType: Type,
            private val lazyTypeSerializer: () -> AMQPSerializer<*>) : PropertySerializer(name, readMethod, resolvedType) {
        // This is lazy so we don't get an infinite loop when a method returns an instance of the class.
        private val typeSerializer: AMQPSerializer<*> by lazy { lazyTypeSerializer() }

        override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ nameForDebug }) {
            if (resolvedType != Any::class.java) {
                typeSerializer.writeClassInfo(output)
            }
        }

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? = ifThrowsAppend({ nameForDebug }) {
            input.readObjectOrNull(obj, schema, resolvedType)
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) = ifThrowsAppend({ nameForDebug }) {
            output.writeObjectOrNull(readMethod!!.invoke(obj), data, resolvedType)
        }

        private val nameForDebug = "$name(${resolvedType.typeName})"
    }

    /**
     * A property serializer for most AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(name: String, readMethod: Method?, resolvedType: Type) : PropertySerializer(name, readMethod, resolvedType) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return if (obj is Binary) obj.array else obj
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            val value = readMethod!!.invoke(obj)
            if (value is ByteArray) {
                data.putObject(Binary(value))
            } else {
                data.putObject(value)
            }
        }
    }

    /**
     * A property serializer for the AMQP char type, needed as a specialisation as the underlying
     * value of the character is stored in numeric UTF-16 form and on deserialisation requires explicit
     * casting back to a char otherwise it's treated as an Integer and a TypeMismatch occurs
     */
    class AMQPCharPropertySerializer(name: String, readMethod: Method?) :
            PropertySerializer(name, readMethod, Character::class.java) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return if (obj == null) null else (obj as Short).toChar()
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            val input = readMethod!!.invoke(obj)
            if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
        }
    }
}

