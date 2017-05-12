package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type


class SingletonSerializer(override val type: Class<*>, val singleton: Any, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"
    private val interfaces = interfacesForSerialization(type) // TODO maybe this proves too much and we need annotations to restrict.

    private fun generateProvides(): List<String> {
        return interfaces.map { it.typeName }
    }

    internal val typeNotation: TypeNotation = RestrictedType(type.typeName, "Singleton", generateProvides(), "boolean", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        data.withDescribed(typeNotation.descriptor) {
            data.putBoolean(false)
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        return singleton
    }
}