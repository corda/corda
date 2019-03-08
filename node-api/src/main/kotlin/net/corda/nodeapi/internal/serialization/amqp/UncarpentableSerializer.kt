package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.CordaRuntimeException
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Serializer injected into a Factories cache for a remote type it failed to synthesise. Should only be used
 * in the context of the evolver when the synthesised type is discarded and is thus a harmless placeholder.
 */
class UncarpentableSerializer(private val name : String) : AMQPSerializer<Any> {
    private val error : String get() = "$name cannot be serialized"

    override val type: Type get() = throw CordaRuntimeException (error)

    override val typeDescriptor: Symbol get() = throw CordaRuntimeException (error)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput): Any {
        throw CordaRuntimeException (error)
    }

    override fun writeClassInfo(output: SerializationOutput) {
        throw CordaRuntimeException (error)
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, debugIndent: Int) {
        throw CordaRuntimeException (error)
    }
}