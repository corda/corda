package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.CordaRuntimeException
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

class Uncarpentable

/**
 * Serializer injected into a Factories cache for a remote type it failed to synthesise. Should only be used
 * in the context of the evolver when the synthesised type is discarded and is thus a harmless placeholder.
 */
class UncarpentableSerializer(private val name : String) : AMQPSerializer<Any> {
    private val error : String get() = "$name cannot be serialized"

    override val type: Type get() = Uncarpentable::class.java

    override val typeDescriptor: Symbol get() = throw CordaRuntimeException (error)

    /**
     * Very explicitly don't do anything with the object stream. Essentially we want this to be
     * a no-op, the code should try to read the missing property from the evolver and just get nothing
     * it cares about back.
     *
     * We need to do this because the evolver in V3 reads all of the properties out of the object stream
     * and then maps those to the local constructor.
     */
    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput): Any {
        return obj
    }

    override fun writeClassInfo(output: SerializationOutput) {
        throw CordaRuntimeException (error)
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, debugIndent: Int) {
        throw CordaRuntimeException (error)
    }
}