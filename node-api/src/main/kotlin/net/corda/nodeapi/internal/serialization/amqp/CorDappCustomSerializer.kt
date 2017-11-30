package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

class CorDappCustomSerializer(
        private val serialiser: SerializationCustomSerializer<*, *>,
        factory: SerializerFactory)
    : AMQPSerializer<Any>, SerializerFor {
    override val revealSubclassesInSchema: Boolean get() = false
    override val type: Type get() = serialiser.type
    override val typeDescriptor = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${nameForType(type)}")
    val descriptor: Descriptor = Descriptor(typeDescriptor)

    private val proxySerializer: ObjectSerializer by lazy { ObjectSerializer(serialiser.ptype, factory) }

    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        @Suppress("UNCHECKED_CAST")
        val proxy = (serialiser as SerializationCustomSerializer<Any?,Any?>).toProxy(obj)

        data.withDescribed(descriptor) {
            data.withList {
                for (property in proxySerializer.propertySerializers) {
                    property.writeProperty(proxy, this, output)
                }
            }
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput) =
            @Suppress("UNCHECKED_CAST")
            (serialiser as SerializationCustomSerializer<Any?,Any?>).fromProxy(
                    uncheckedCast(proxySerializer.readObject(obj, schema, input)))!!

    override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == type
}

