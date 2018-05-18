package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Implemented to serialize and deserialize different types of objects to/from AMQP.
 */
interface AMQPSerializer<out T> {
    /**
     * The JVM type this can serialize and deserialize.
     */
    val type: Type

    /**
     * Textual unique representation of the JVM type this represents.  Will be encoded into the AMQP stream and
     * will appear in the schema.
     *
     * This should be unique enough that we can use one global cache of [AMQPSerializer]s and use this as the look up key.
     */
    val typeDescriptor: Symbol

    /**
     * Add anything required to the AMQP schema via [SerializationOutput.writeTypeNotations] and any dependent serializers
     * via [SerializationOutput.requireSerializer]. e.g. for the elements of an array.
     */
    fun writeClassInfo(output: SerializationOutput)

    /**
     * Write the given object, with declared type, to the output.
     */
    @JvmDefault
    fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                    context: SerializationContext, debugIndent: Int = 0)

    /**
     * Read the given object from the input. The envelope is provided in case the schema is required.
     */
    fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): T
}
