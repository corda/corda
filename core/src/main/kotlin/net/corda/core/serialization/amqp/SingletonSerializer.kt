package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * A custom serializer that transports nothing on the wire (except a boolean "false", since AMQP does not support
 * absolutely nothing, or null as a described type) when we have a singleton within the node that we just
 * want converting back to that singleton instance on the receiving JVM.
 */
class SingletonSerializer(override val type: Class<*>, val singleton: Any, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"
    private val interfaces = interfacesForSerialization(type)

    private fun generateProvides(): List<String> = interfaces.map { it.typeName }

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